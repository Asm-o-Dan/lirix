import re

file_path = r"c:\Users\DaniilTuT\Documents\antigravity\quick-nobel\app\src\main\java\com\lirix\app\ui\NowPlayingScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    lines = f.readlines()

out = []
i = 0
n = len(lines)

while i < n:
    line = lines[i]

    # Loading spinner
    if "CircularProgressIndicator(" in line:
        out.append(line)
        i += 1
        if i < n and "color = AppColors.HyperViolet" in lines[i]:
            out.append(lines[i].replace("color = AppColors.HyperViolet", "color = lirixColors.accentPrimary"))
            i += 1
            continue

    # ExhaustedLyricsPlaceholder
    if "fun ExhaustedLyricsPlaceholder(" in line:
        out.append(line)
        i += 1
        # Process the placeholder body until closing brace
        brace_count = 1
        while i < n and brace_count > 0:
            cur = lines[i]
            brace_count += cur.count("{") - cur.count("}")

            cur = cur.replace("AppColors.TextTertiary", "tokens.textMuted")
            cur = cur.replace("AppColors.TextPrimary", "tokens.textPrimary")
            cur = cur.replace("AppColors.TextSecondary", "tokens.textSecondary")
            cur = cur.replace("containerColor = AppColors.HyperViolet", "containerColor = tokens.accentPrimary")
            cur = cur.replace('color = AppColors.AmoledBlack, fontWeight = FontWeight.Bold', 'color = if (tokens.isDark) Color.Black else Color.White, fontWeight = FontWeight.Bold')
            cur = cur.replace("border = BorderStroke(1.dp, AppColors.CyberCyan)", "border = BorderStroke(1.dp, tokens.accentSecondary)")
            cur = cur.replace('color = AppColors.CyberCyan', 'color = tokens.accentSecondary')
            cur = cur.replace("border = BorderStroke(1.dp, AppColors.HyperViolet.copy(alpha = 0.5f))", "border = BorderStroke(1.dp, tokens.accentPrimary.copy(alpha = 0.6f))")
            cur = cur.replace("tint = AppColors.HyperViolet,", "tint = tokens.accentPrimary,")

            if "horizontalAlignment = Alignment.CenterHorizontally," in cur:
                out.append(cur)
                i += 1
                out.append(lines[i]) # verticalArrangement = Arrangement.Center
                i += 1
                out.append("    ) {\n")
                out.append("        val tokens = LirixThemeTokens.colors\n")
                i += 1
                continue

            out.append(cur)
            i += 1
        continue

    # NOTES tab (AppColors.ElectricMint and surface)
    if "Surface(" in line and i + 1 < n and "color = AppColors.SurfaceLevel2" in lines[i+1]:
        out.append(line)
        i += 1
        out.append(lines[i].replace("AppColors.SurfaceLevel2", "lirixColors.surfaceCard"))
        i += 1
        continue

    if "AppColors.ElectricMint" in line:
        line = line.replace("AppColors.ElectricMint", "lirixColors.accentPrimary")
    if "AppColors.SurfaceLevel1" in line:
        line = line.replace("AppColors.SurfaceLevel1", "lirixColors.surfacePrimary")
    if "AppColors.SurfaceLevel2" in line:
        line = line.replace("AppColors.SurfaceLevel2", "lirixColors.surfaceCard")

    out.append(line)
    i += 1

with open(file_path, "w", encoding="utf-8") as f:
    f.writelines(out)

print("Patch applied to NowPlayingScreen.kt successfully!")
