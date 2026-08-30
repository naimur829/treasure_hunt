import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;

// Main Game Window Class
public class TreasureHuntGame extends JFrame {

    public TreasureHuntGame() {
        setTitle("Treasure Hunt Game - Week 5: Island & Movement");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        GamePanel gamePanel = new GamePanel();
        add(gamePanel);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TreasureHuntGame());
    }
}

// 1. MAP CLASS (Procedural Island Generator)
class Map {
    public static final int TILE_SIZE = 40;
    public static final int ROWS = 15;
    public static final int COLS = 20;

    // Tile Types: 0 = Water, 1 = Sand, 2 = Grass
    private int[][] grid;

    public Map() {
        grid = new int[ROWS][COLS];
        generateProceduralIsland();
    }

    private void generateProceduralIsland() {
        Random rand = new Random();

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                // Outer borders are Water
                if (r <= 1 || r >= ROWS - 2 || c <= 1 || c >= COLS - 2) {
                    grid[r][c] = 0; // Water
                } 
                // Shore areas are Sand
                else if (r == 2 || r == ROWS - 3 || c == 2 || c == COLS - 3) {
                    grid[r][c] = (rand.nextInt(10) > 2) ? 1 : 0; // Sand with slight variation
                } 
                // Center island area is Grass
                else {
                    grid[r][c] = (rand.nextInt(10) > 1) ? 2 : 1; // Grass with sand patches
                }
            }
        }
    }

    public boolean isWalkable(int gridX, int gridY) {
        if (gridX < 0 || gridX >= COLS || gridY < 0 || gridY >= ROWS) {
            return false;
        }
        return grid[gridY][gridX] != 0; // Cannot walk on Water
    }

    public void draw(Graphics2D g2d) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int tileType = grid[r][c];
                switch (tileType) {
                    case 0:
                        g2d.setColor(new Color(30, 144, 255)); // Water Blue
                        break;
                    case 1:
                        g2d.setColor(new Color(238, 214, 175)); // Sand Color
                        break;
                    case 2:
                        g2d.setColor(new Color(60, 179, 113)); // Island Grass Green
                        break;
                }
                g2d.fillRect(c * TILE_SIZE, r * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                
                // Draw tile borders for grid visual
                g2d.setColor(new Color(0, 0, 0, 30));
                g2d.drawRect(c * TILE_SIZE, r * TILE_SIZE, TILE_SIZE, TILE_SIZE);
            }
        }
    }
}

// 2. PLAYER CLASS (Movement & State Handling)
class Player {
    private int gridX;
    private int gridY;
    private String direction;

    public Player(int startX, int startY) {
        this.gridX = startX;
        this.gridY = startY;
        this.direction = "DOWN";
    }

    public void move(int dx, int dy, Map map) {
        int nextX = gridX + dx;
        int nextY = gridY + dy;

        // Direction state update
        if (dx > 0) direction = "RIGHT";
        else if (dx < 0) direction = "LEFT";
        else if (dy > 0) direction = "DOWN";
        else if (dy < 0) direction = "UP";

        // State validation & boundary collision check
        if (map.isWalkable(nextX, nextY)) {
            gridX = nextX;
            gridY = nextY;
        }
    }

    public void draw(Graphics2D g2d) {
        int pixelX = gridX * Map.TILE_SIZE;
        int pixelY = gridY * Map.TILE_SIZE;

        // Player Body
        g2d.setColor(new Color(220, 20, 60)); // Crimson Player
        g2d.fillOval(pixelX + 6, pixelY + 6, Map.TILE_SIZE - 12, Map.TILE_SIZE - 12);

        // Player Facing Indicator (Direction State Visual)
        g2d.setColor(Color.WHITE);
        int eyeX = pixelX + Map.TILE_SIZE / 2;
        int eyeY = pixelY + Map.TILE_SIZE / 2;

        switch (direction) {
            case "UP": eyeY -= 8; break;
            case "DOWN": eyeY += 8; break;
            case "LEFT": eyeX -= 8; break;
            case "RIGHT": eyeX += 8; break;
        }
        g2d.fillOval(eyeX - 3, eyeY - 3, 6, 6);
    }
}

// 3. GAME PANEL CLASS (Game Loop & Controls Wiring)
class GamePanel extends JPanel implements ActionListener, KeyListener {
    private static final int WIDTH = Map.COLS * Map.TILE_SIZE;
    private static final int HEIGHT = Map.ROWS * Map.TILE_SIZE;

    private Timer timer;
    private Map map;
    private Player player;

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);

        map = new Map();
        player = new Player(Map.COLS / 2, Map.ROWS / 2); // Start at island center

        // Game Loop (~60 FPS)
        timer = new Timer(16, this);
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw Map & Player
        map.draw(g2d);
        player.draw(g2d);

        // Instruction Overlay
        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.fillRect(10, 10, 360, 30);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 13));
        g2d.drawString("Controls: WASD / Arrow Keys to Move Player", 20, 30);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        repaint();
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();

        // Wiring WASD & Arrow Key Controls
        if (key == KeyEvent.VK_W || key == KeyEvent.VK_UP) {
            player.move(0, -1, map);
        } else if (key == KeyEvent.VK_S || key == KeyEvent.VK_DOWN) {
            player.move(0, 1, map);
        } else if (key == KeyEvent.VK_A || key == KeyEvent.VK_LEFT) {
            player.move(-1, 0, map);
        } else if (key == KeyEvent.VK_D || key == KeyEvent.VK_RIGHT) {
            player.move(1, 0, map);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void keyTyped(KeyEvent e) {}
}