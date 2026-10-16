# CR-CON-PCM-03 — replace simulated handoff with actual audio capture

APPROVED by root under the user's direct implementation instruction, 2026-10-16.

Device evidence: Windows TCP established from the phone; Android MediaProjection is null; no AudioCastForegroundService and no capture-start log. The user reports green connected status with no audio. DeviceConnectBottomSheet.onDeviceClick fabricated HandoffAck and RemoteStateUpdate after delays instead of invoking capture.

Change one UI route: selecting a paired device requests RECORD_AUDIO and MediaProjection through the existing Audio Cast path. Remove synthesized runtime success messages. Resume the local playback state before requesting capture, because the actual media source remains the phone. Device playback indication follows real AndroidCastSourceManager.isCasting and its target rather than simulated handoff state. No claim that capture-start proves audible output.

Validation: compile APK; install with data preserved; tap device and obtain actual system consent; observe capture service/MediaProjection/logs, then receiver packets/output and user-confirmed sound. Release remains held pending this physical smoke.

AudioBridge reference supplied by the user: https://github.com/Lyten02/AudioBridge. Its documented main route is PC audio to phone, with phone microphone to PC. Useful reference layers: dedicated sender, Opus codec, packet sequence/jitter buffer, adaptive playout, authenticated session. It does not directly replace Android playback-capture permission/lifecycle. No Rust port is needed to repair this UI routing defect.
