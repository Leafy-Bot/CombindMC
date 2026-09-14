import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class GenTextures {
    public static void main(String[] args) throws Exception {
        String base = "C:\\Users\\Win1122h2\\Desktop\\CODE\\autobridge\\test-mods-src\\testmod\\assets\\testmod\\textures";

        // Item textures (16x16)
        createTexture(base + "\\items\\certus_quartz.png", new Color(180, 220, 255), "CQ");
        createTexture(base + "\\items\\fluix_crystal.png", new Color(200, 100, 255), "FC");
        createTexture(base + "\\items\\sky_stone_dust.png", new Color(60, 60, 80), "SD");

        // Block textures (16x16)
        createTexture(base + "\\block\\me_controller.png", new Color(100, 150, 200), "MC");
        createTexture(base + "\\block\\sky_stone_block.png", new Color(40, 40, 50), "SB");
        createTexture(base + "\\block\\fluix_block.png", new Color(150, 80, 200), "FB");

        System.out.println("Generated 6 textures");
    }

    static void createTexture(String path, Color bg, String label) throws Exception {
        new File(path).getParentFile().mkdirs();
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(bg);
        g.fillRect(0, 0, 16, 16);
        g.setColor(bg.darker());
        g.setStroke(new BasicStroke(1));
        g.drawRect(0, 0, 15, 15);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, (16 - fm.stringWidth(label)) / 2, (16 - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
        ImageIO.write(img, "PNG", new File(path));
    }
}
