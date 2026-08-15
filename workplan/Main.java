import javax.swing.*;
import java.awt.*;

public class Main {

    // ---------- Constants ----------
    static class Constants {
        public static final int WINDOW_WIDTH = 800;
        public static final int WINDOW_HEIGHT = 600;
        public static final String GAME_TITLE = "Treasure Hunt";
    }

    // ---------- Game Panel ----------
    static class GamePanel extends JPanel {
        public GamePanel() {
            setPreferredSize(new Dimension(Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT));
            setBackground(new Color(20, 40, 70)); // dark ocean-like theme
            setFocusable(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 18));
            g.drawString("Treasure Hunt - Game Panel Ready", 20, 30);
        }
    }

    // ---------- Game Window ----------
    static class GameWindow extends JFrame {
        public GameWindow() {
            setTitle(Constants.GAME_TITLE);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setResizable(false);

            GamePanel gamePanel = new GamePanel();
            add(gamePanel);

            pack();
            setLocationRelativeTo(null); // center on screen
            setVisible(true);
        }
    }

    // ---------- Main Method ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameWindow());
    }
}