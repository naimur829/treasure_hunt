import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Main {

    // ---------- Constants / Theme ----------
    static class Constants {
        public static final int WINDOW_WIDTH = 800;
        public static final int WINDOW_HEIGHT = 600;
        public static final String GAME_TITLE = "Treasure Hunt";

        public static final Color BG_COLOR = new Color(20, 40, 70);   // dark ocean theme
        public static final Color TEXT_COLOR = Color.WHITE;
        public static final Font TITLE_FONT = new Font("Arial", Font.BOLD, 20);

        public static final int FPS = 60;
        public static final int DELAY = 1000 / FPS; // Timer delay in ms
    }

    // ---------- Game Panel (Canvas) ----------
    static class GamePanel extends JPanel implements ActionListener {

        private Timer gameTimer;
        private int frameCount = 0;

        public GamePanel() {
            setPreferredSize(new Dimension(Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT));
            setBackground(Constants.BG_COLOR);
            setFocusable(true);

            // ---- Game Loop (Timer-based) ----
            gameTimer = new Timer(Constants.DELAY, this);
            gameTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            g2d.setColor(Constants.TEXT_COLOR);
            g2d.setFont(Constants.TITLE_FONT);
            g2d.drawString("Treasure Hunt - Game Panel Ready", 20, 30);
            g2d.drawString("Frame: " + frameCount, 20, 60);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            frameCount++;      // update game state
            repaint();         // redraw panel each tick
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
            setLocationRelativeTo(null);
            setVisible(true);
        }
    }

    // ---------- Main Method ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameWindow());
    }
}