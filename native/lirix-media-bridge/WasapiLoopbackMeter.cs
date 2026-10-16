using System.Diagnostics;
using System.Runtime.InteropServices;

namespace LirixMediaBridge;

/// <summary>
/// WASAPI Loopback Peak Audio Meter using Windows Core Audio API (IAudioMeterInformation).
/// Provides authentic Left/Right channel audio levels in dBFS (-60..0 dBFS) without any simulation.
/// </summary>
public sealed class WasapiLoopbackMeter : IDisposable
{
    private static readonly Guid CLSID_MMDeviceEnumerator = new("BCDE0395-E52F-467C-8E3D-C4579291692E");
    private static readonly Guid IID_IMMDeviceEnumerator = new("A95664D2-9614-4F35-A746-DE8DB63617E6");
    private static readonly Guid IID_IAudioMeterInformation = new("C02216F6-8C67-4B5B-9D00-D008E73E0064");

    private const uint CLSCTX_INPROC_SERVER = 0x1;
    private const uint CLSCTX_ALL = 0x17;
    private const int EDataFlow_Render = 0;
    private const int ERole_Multimedia = 1;
    private const int ERole_Console = 0;

    private readonly Action<MeterMessage> _onMeterUpdated;
    private readonly Thread _workerThread;
    private readonly CancellationTokenSource _cts = new();
    private bool _disposed;

    public WasapiLoopbackMeter(Action<MeterMessage> onMeterUpdated)
    {
        _onMeterUpdated = onMeterUpdated ?? throw new ArgumentNullException(nameof(onMeterUpdated));
        _workerThread = new Thread(WorkerLoop)
        {
            Name = "LirixWasapiAudioMeterThread",
            IsBackground = true,
            Priority = ThreadPriority.AboveNormal
        };
    }

    public void Start()
    {
        if (_disposed) throw new ObjectDisposedException(nameof(WasapiLoopbackMeter));
        _workerThread.Start();
    }

    private void WorkerLoop()
    {
        // Initialize COM on dedicated thread
        int coInit = CoInitializeEx(IntPtr.Zero, 0 /* COINIT_MULTITHREADED */);

        IntPtr pEnumerator = IntPtr.Zero;
        IntPtr pDevice = IntPtr.Zero;
        IntPtr pMeter = IntPtr.Zero;
        uint channelCount = 2;
        float[]? channelBuffer = null;

        bool wasActive = false;
        int silentCycles = 0;

        try
        {
            while (!_cts.IsCancellationRequested)
            {
                // Ensure COM meter interface is acquired
                if (pMeter == IntPtr.Zero)
                {
                    SafeRelease(ref pMeter);
                    SafeRelease(ref pDevice);
                    SafeRelease(ref pEnumerator);

                    int hr = CoCreateInstance(
                        in CLSID_MMDeviceEnumerator,
                        IntPtr.Zero,
                        CLSCTX_INPROC_SERVER,
                        in IID_IMMDeviceEnumerator,
                        out pEnumerator);

                    if (hr >= 0 && pEnumerator != IntPtr.Zero)
                    {
                        // Get default audio endpoint (Render + Multimedia)
                        hr = CallGetDefaultAudioEndpoint(pEnumerator, EDataFlow_Render, ERole_Multimedia, out pDevice);
                        if (hr < 0 || pDevice == IntPtr.Zero)
                        {
                            // Fallback to Console role
                            hr = CallGetDefaultAudioEndpoint(pEnumerator, EDataFlow_Render, ERole_Console, out pDevice);
                        }

                        if (hr >= 0 && pDevice != IntPtr.Zero)
                        {
                            // Activate IAudioMeterInformation
                            hr = CallActivateMeter(pDevice, in IID_IAudioMeterInformation, CLSCTX_INPROC_SERVER, out pMeter);
                            if (hr >= 0 && pMeter != IntPtr.Zero)
                            {
                                if (CallGetMeteringChannelCount(pMeter, out uint count) >= 0 && count > 0)
                                {
                                    channelCount = count;
                                }
                                else
                                {
                                    channelCount = 2;
                                }
                                channelBuffer = new float[channelCount];
                            }
                        }
                    }

                    if (pMeter == IntPtr.Zero)
                    {
                        // Device offline or no audio endpoint available
                        if (wasActive)
                        {
                            EmitOffline();
                            wasActive = false;
                        }
                        Thread.Sleep(1000);
                        continue;
                    }
                }

                // Query peak values from WASAPI
                if (channelBuffer == null || channelBuffer.Length != channelCount)
                {
                    channelBuffer = new float[channelCount];
                }

                int readHr = CallGetChannelsPeakValues(pMeter, channelCount, channelBuffer);
                if (readHr < 0)
                {
                    // Device disconnected / invalidated (e.g. unplugged headphones, sleep)
                    SafeRelease(ref pMeter);
                    SafeRelease(ref pDevice);
                    if (wasActive)
                    {
                        EmitOffline();
                        wasActive = false;
                    }
                    Thread.Sleep(500);
                    continue;
                }

                float leftPeak = channelBuffer[0];
                float rightPeak = (channelCount > 1) ? channelBuffer[1] : channelBuffer[0];

                // Amplitude in [0.0..1.0] translated to dBFS: 20 * log10(amplitude)
                // Range: -60.0 dBFS to 0.0 dBFS
                float leftDbfs = (leftPeak > 0.001f) ? Math.Clamp(20.0f * MathF.Log10(leftPeak), -60.0f, 0.0f) : -60.0f;
                float rightDbfs = (rightPeak > 0.001f) ? Math.Clamp(20.0f * MathF.Log10(rightPeak), -60.0f, 0.0f) : -60.0f;
                float masterPeak = Math.Max(leftPeak, rightPeak);
                float masterDbfs = Math.Max(leftDbfs, rightDbfs);

                // Clipping detected if dBFS > -1.0 dBFS
                bool isClipping = (leftDbfs > -1.0f || rightDbfs > -1.0f);
                bool isActive = (leftPeak > 0.0001f || rightPeak > 0.0001f);

                if (isActive)
                {
                    silentCycles = 0;
                    wasActive = true;
                    _onMeterUpdated(new MeterMessage
                    {
                        LeftDbfs = (float)Math.Round(leftDbfs, 2),
                        RightDbfs = (float)Math.Round(rightDbfs, 2),
                        LeftPeak = (float)Math.Round(leftPeak, 4),
                        RightPeak = (float)Math.Round(rightPeak, 4),
                        MasterPeak = (float)Math.Round(masterPeak, 4),
                        MasterDbfs = (float)Math.Round(masterDbfs, 2),
                        IsClipping = isClipping,
                        IsActive = true
                    });
                }
                else
                {
                    silentCycles++;
                    // When music pauses or sound stops, emit zero/silence decay immediately
                    if (silentCycles <= 3)
                    {
                        _onMeterUpdated(new MeterMessage
                        {
                            LeftDbfs = -60.0f,
                            RightDbfs = -60.0f,
                            LeftPeak = 0.0f,
                            RightPeak = 0.0f,
                            MasterPeak = 0.0f,
                            MasterDbfs = -60.0f,
                            IsClipping = false,
                            IsActive = false
                        });
                        wasActive = false;
                    }
                    else if (silentCycles % 8 == 0)
                    {
                        // Periodically reaffirm idle status with minimal stdout traffic
                        _onMeterUpdated(new MeterMessage
                        {
                            LeftDbfs = -60.0f,
                            RightDbfs = -60.0f,
                            LeftPeak = 0.0f,
                            RightPeak = 0.0f,
                            MasterPeak = 0.0f,
                            MasterDbfs = -60.0f,
                            IsClipping = false,
                            IsActive = false
                        });
                    }
                }

                // Poll rate: 30-33 Hz (30-33ms) when active; 50ms when silent
                Thread.Sleep(isActive ? 30 : 50);
            }
        }
        catch (ThreadAbortException) { }
        catch (Exception ex)
        {
            Debug.WriteLine($"[WasapiLoopbackMeter] Worker error: {ex}");
        }
        finally
        {
            SafeRelease(ref pMeter);
            SafeRelease(ref pDevice);
            SafeRelease(ref pEnumerator);

            if (coInit >= 0)
            {
                CoUninitialize();
            }
        }
    }

    private void EmitOffline()
    {
        _onMeterUpdated(new MeterMessage
        {
            LeftDbfs = -60.0f,
            RightDbfs = -60.0f,
            LeftPeak = 0.0f,
            RightPeak = 0.0f,
            MasterPeak = 0.0f,
            MasterDbfs = -60.0f,
            IsClipping = false,
            IsActive = false
        });
    }

    private static unsafe int CallGetDefaultAudioEndpoint(IntPtr pEnumerator, int dataFlow, int role, out IntPtr ppEndpoint)
    {
        ppEndpoint = IntPtr.Zero;
        if (pEnumerator == IntPtr.Zero) return -1;
        IntPtr* vtable = *(IntPtr**)pEnumerator;
        // Slot 4: GetDefaultAudioEndpoint(this, dataFlow, role, &ppEndpoint)
        var func = (delegate* unmanaged[Stdcall]<IntPtr, int, int, IntPtr*, int>)vtable[4];
        fixed (IntPtr* pOut = &ppEndpoint)
        {
            return func(pEnumerator, dataFlow, role, pOut);
        }
    }

    private static unsafe int CallActivateMeter(IntPtr pDevice, in Guid iid, uint dwClsCtx, out IntPtr ppInterface)
    {
        ppInterface = IntPtr.Zero;
        if (pDevice == IntPtr.Zero) return -1;
        IntPtr* vtable = *(IntPtr**)pDevice;
        // Slot 3: Activate(this, in iid, dwClsCtx, IntPtr.Zero, &ppInterface)
        var func = (delegate* unmanaged[Stdcall]<IntPtr, Guid*, uint, IntPtr, IntPtr*, int>)vtable[3];
        fixed (Guid* pIid = &iid)
        fixed (IntPtr* pOut = &ppInterface)
        {
            return func(pDevice, pIid, dwClsCtx, IntPtr.Zero, pOut);
        }
    }

    private static unsafe int CallGetMeteringChannelCount(IntPtr pMeter, out uint pnChannelCount)
    {
        pnChannelCount = 0;
        if (pMeter == IntPtr.Zero) return -1;
        IntPtr* vtable = *(IntPtr**)pMeter;
        // Slot 4: GetMeteringChannelCount(this, &pnChannelCount)
        var func = (delegate* unmanaged[Stdcall]<IntPtr, uint*, int>)vtable[4];
        fixed (uint* pOut = &pnChannelCount)
        {
            return func(pMeter, pOut);
        }
    }

    private static unsafe int CallGetChannelsPeakValues(IntPtr pMeter, uint u32ChannelCount, float[] afPeakValues)
    {
        if (pMeter == IntPtr.Zero || afPeakValues == null) return -1;
        IntPtr* vtable = *(IntPtr**)pMeter;
        // Slot 5: GetChannelsPeakValues(this, u32ChannelCount, float* afPeakValues)
        var func = (delegate* unmanaged[Stdcall]<IntPtr, uint, float*, int>)vtable[5];
        fixed (float* pBuf = afPeakValues)
        {
            return func(pMeter, u32ChannelCount, pBuf);
        }
    }

    private static unsafe void SafeRelease(ref IntPtr pUnk)
    {
        if (pUnk != IntPtr.Zero)
        {
            try
            {
                IntPtr* vtable = *(IntPtr**)pUnk;
                // Slot 2: Release(this)
                var releaseFunc = (delegate* unmanaged[Stdcall]<IntPtr, uint>)vtable[2];
                releaseFunc(pUnk);
            }
            catch { }
            pUnk = IntPtr.Zero;
        }
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        _cts.Cancel();
        if (_workerThread.IsAlive)
        {
            _workerThread.Join(500);
        }
        _cts.Dispose();
    }

    [DllImport("ole32.dll", ExactSpelling = true)]
    private static extern int CoInitializeEx(IntPtr pvReserved, uint dwCoInit);

    [DllImport("ole32.dll", ExactSpelling = true)]
    private static extern void CoUninitialize();

    [DllImport("ole32.dll", ExactSpelling = true)]
    private static extern int CoCreateInstance(
        in Guid rclsid,
        IntPtr pUnkOuter,
        uint dwClsContext,
        in Guid riid,
        out IntPtr ppv);
}
