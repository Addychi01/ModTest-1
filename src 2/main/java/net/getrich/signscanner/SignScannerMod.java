package net.getrich.signscanner;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.SignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class SignScannerMod implements ClientModInitializer {
    private final Set<BlockPos> scanned = new HashSet<>();
    private String lastMessage = "";
    private long messageTimestamp = 0;

    @Override
    public void onInitializeClient() {
        System.out.println("[SignScanner] Initializing with HUD overlay");

        HudRenderCallback.EVENT.register(this::renderHud);

        Thread scanThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2000);

                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player == null || client.world == null) continue;

                    BlockPos playerPos = client.player.getBlockPos();
                    World world = client.world;

                    for (int dx = -16; dx <= 16; dx++) {
                        for (int dy = -4; dy <= 4; dy++) {
                            for (int dz = -16; dz <= 16; dz++) {
                                BlockPos pos = playerPos.add(dx, dy, dz);
                                if (scanned.contains(pos)) continue;

                                if (world.getBlockState(pos).getBlock() instanceof SignBlock) {
                                    var entity = world.getBlockEntity(pos);
                                    if (entity instanceof SignBlockEntity sign) {
                                        Text[] lines = sign.getText();
                                        StringBuilder sb = new StringBuilder();
                                        for (Text t : lines) sb.append(t.getString()).append(" | ");
                                        String text = sb.toString();

                                        if (text.contains("QuickShop") || text.contains("[QuickShop]")) {
                                            String out = String.format("Shop at %s: %s",
                                                pos.toShortString(), text);
                                            System.out.println("[SignScanner] " + out);
                                            appendToFile("quickshop_signs.txt", "[SignScanner] " + out);
                                            scanned.add(pos);
                                            lastMessage = out;
                                            messageTimestamp = System.currentTimeMillis();
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    System.err.println("[SignScanner] Scan interrupted!");
                    break;
                }
            }
        });
        scanThread.setDaemon(true);
        scanThread.start();
    }

    private void renderHud(MatrixStack matrices, float tickDelta) {
        if (!lastMessage.isEmpty() &&
            System.currentTimeMillis() - messageTimestamp < 3000) {
            MinecraftClient client = MinecraftClient.getInstance();
            TextRenderer textRenderer = client.textRenderer;
            int x = 10;
            int y = 10;
            textRenderer.drawWithShadow(matrices, lastMessage, x, y, 0xFFFFFF);
        }
    }

    private void appendToFile(String fileName, String text) {
        try (FileWriter fw = new FileWriter(fileName, true)) {
            fw.write(text);
            fw.write(System.lineSeparator());
        } catch (IOException e) {
            System.err.println("[SignScanner] Failed to write: " + e.getMessage());
        }
    }
}
