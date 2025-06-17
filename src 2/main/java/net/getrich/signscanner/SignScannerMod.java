package net.yourdomain.signscanner;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.block.SignBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.block.entity.SignBlockEntity;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class SignScannerMod implements ClientModInitializer {

    private final Pattern quickshopPattern = Pattern.compile(".*(\\d+\\s*x\\s*\\w+|\\d+g|buy|sell|price).*", Pattern.CASE_INSENSITIVE);
    private final Set<BlockPos> scanned = new HashSet<>();

    private String lastMessage = "";
    private long messageTimestamp = 0;

    @Override
    public void onInitializeClient() {
        System.out.println("[SignScanner] Initializing...");
        HudRenderCallback.EVENT.register(this::renderHud);
        new Thread(this::scanLoop).start();
    }

    private void scanLoop() {
        while (true) {
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.world != null && client.player != null) {
                    BlockPos playerPos = client.player.getBlockPos();
                    scanNearbySigns(client.world, playerPos);
                }
                Thread.sleep(5000); // Scan every 5 seconds
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void scanNearbySigns(ClientWorld world, BlockPos center) {
        int radius = 10;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (scanned.contains(pos)) continue;

                    BlockState state = world.getBlockState(pos);
                    if (state.getBlock() instanceof SignBlock) {
                        var entity = world.getBlockEntity(pos);
                        if (entity instanceof SignBlockEntity sign) {
                            Text[] lines = sign.getText();
                            StringBuilder sb = new StringBuilder();
                            for (Text t : lines) sb.append(t.getString()).append(" | ");
                            String fullText = sb.toString().trim();

                            if (quickshopPattern.matcher(fullText).find()) {
                                String out = String.format("Shop at %s: %s", pos.toShortString(), fullText);
                                System.out.println("[SignScanner] " + out);
                                appendToFile("quickshop_signs.txt", "[SignScanner] " + out);
                                lastMessage = out;
                                messageTimestamp = System.currentTimeMillis();
                                scanned.add(pos);
                            }
                        }
                    }
                }
            }
        }
    }

    private void appendToFile(String filename, String text) {
        try (FileWriter fw = new FileWriter(filename, true)) {
            fw.write(text + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void renderHud(MatrixStack matrices, float tickDelta) {
        if (!lastMessage.isEmpty() && System.currentTimeMillis() - messageTimestamp < 3000) {
            MinecraftClient client = MinecraftClient.getInstance();
            TextRenderer textRenderer = client.textRenderer;
            textRenderer.drawWithShadow(matrices, lastMessage, 10, 10, 0xFFFF00);
        }
    }
}
