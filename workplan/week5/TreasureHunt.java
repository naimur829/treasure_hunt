import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class TreasureHunt extends JFrame {

    public TreasureHunt() {
        setTitle("Treasure Hunt");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        GamePanel gamePanel = new GamePanel();
        add(gamePanel);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        gamePanel.requestFocusInWindow();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TreasureHunt::new);
    }
}

class GamePanel extends JPanel {

    // ---- Grid settings ----
    static final int TILE_SIZE = 44;
    static final int GRID_COLS = 18;
    static final int GRID_ROWS = 14;
    static final int PANEL_WIDTH = TILE_SIZE * GRID_COLS;
    static final int PANEL_HEIGHT = TILE_SIZE * GRID_ROWS + 50; // +50 for score bar

    // ---- Tile types (more variety = more complex map) ----
    static final int DEEP_WATER = 0;
    static final int SHALLOW_WATER = 1;
    static final int SAND = 2;
    static final int GRASS = 3;
    static final int FOREST = 4;
    static final int ROCK = 5;

    private int[][] islandMap;
    // per-tile random seed (0-99) used to draw consistent texture detail (speckles, waves, trees)
    private int[][] tileDetail;

    // ---- Player state ----
    private int playerCol = GRID_COLS / 2;
    private int playerRow = GRID_ROWS / 2;

    // ---- Score ----
    private int score = 0;

    public GamePanel() {
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setFocusable(true);
        setBackground(Color.BLACK);

        generateIslandMap();
        placePlayerOnLand();

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleInput(e.getKeyCode());
            }
        });
    }

    /*
     * Complex island generation using layered "blob" noise instead of a
     * perfect circle. Several random elevation bumps are summed together,
     * which produces an organic, irregular coastline with bays, a couple
     * of small outlying islets, mountains inland, and forest patches.
     */
    private void generateIslandMap() {
        islandMap = new int[GRID_ROWS][GRID_COLS];
        tileDetail = new int[GRID_ROWS][GRID_COLS];

        java.util.Random rand = new java.util.Random(); // remove seed arg for a new map every run

        double[][] elevation = new double[GRID_ROWS][GRID_COLS];

        // 1) Main landmass blobs (a handful of overlapping bumps near the center)
        int mainBlobs = 5 + rand.nextInt(3);
        for (int i = 0; i < mainBlobs; i++) {
            double cr = GRID_ROWS / 2.0 + (rand.nextDouble() - 0.5) * GRID_ROWS * 0.35;
            double cc = GRID_COLS / 2.0 + (rand.nextDouble() - 0.5) * GRID_COLS * 0.35;
            double radius = 2.5 + rand.nextDouble() * 3.0;
            double strength = 0.7 + rand.nextDouble() * 0.6;
            addBlob(elevation, cr, cc, radius, strength);
        }

        // 2) A couple of small islets further out, for visual interest
        int islets = rand.nextInt(3);
        for (int i = 0; i < islets; i++) {
            double cr = rand.nextDouble() * GRID_ROWS;
            double cc = rand.nextDouble() * GRID_COLS;
            double radius = 1.0 + rand.nextDouble() * 1.3;
            addBlob(elevation, cr, cc, radius, 0.9);
        }

        // 3) Convert elevation -> tile type using thresholds
        for (int r = 0; r < GRID_ROWS; r++) {
            for (int c = 0; c < GRID_COLS; c++) {
                double h = elevation[r][c];
                tileDetail[r][c] = rand.nextInt(100);

                if (h < 0.18) {
                    islandMap[r][c] = DEEP_WATER;
                } else if (h < 0.32) {
                    islandMap[r][c] = SHALLOW_WATER;
                } else if (h < 0.42) {
                    islandMap[r][c] = SAND;
                } else if (h < 0.75) {
                    // grass, with patches becoming forest based on local noise
                    islandMap[r][c] = (tileDetail[r][c] < 28) ? FOREST : GRASS;
                } else {
                    islandMap[r][c] = ROCK;
                }
            }
        }
    }

    private void addBlob(double[][] elevation, double centerRow, double centerCol,
                          double radius, double strength) {
        for (int r = 0; r < GRID_ROWS; r++) {
            for (int c = 0; c < GRID_COLS; c++) {
                double dr = r - centerRow;
                double dc = c - centerCol;
                double dist = Math.sqrt(dr * dr + dc * dc);
                double falloff = Math.max(0, 1.0 - dist / radius);
                // smoothstep-ish falloff for softer, less circular edges
                falloff = falloff * falloff * (3 - 2 * falloff);
                elevation[r][c] += falloff * strength;
            }
        }
    }

    // Make sure the player starts on walkable land, not in the water
    private void placePlayerOnLand() {
        if (isWalkable(playerRow, playerCol)) return;

        for (int r = 0; r < GRID_ROWS; r++) {
            for (int c = 0; c < GRID_COLS; c++) {
                if (isWalkable(r, c)) {
                    playerRow = r;
                    playerCol = c;
                    return;
                }
            }
        }
    }

    private boolean isWalkable(int r, int c) {
        int t = islandMap[r][c];
        return t == SAND || t == GRASS || t == FOREST || t == ROCK;
    }

    private void handleInput(int keyCode) {
        int newRow = playerRow;
        int newCol = playerCol;

        switch (keyCode) {
            case KeyEvent.VK_W:
            case KeyEvent.VK_UP:
                newRow--;
                break;
            case KeyEvent.VK_S:
            case KeyEvent.VK_DOWN:
                newRow++;
                break;
            case KeyEvent.VK_A:
            case KeyEvent.VK_LEFT:
                newCol--;
                break;
            case KeyEvent.VK_D:
            case KeyEvent.VK_RIGHT:
                newCol++;
                break;
            default:
                return; // key match na korle kichu hobe na
        }

        // Grid er bhitore ache kina check
        if (newRow < 0 || newRow >= GRID_ROWS || newCol < 0 || newCol >= GRID_COLS) {
            return;
        }

        // Pani (deep/shallow water) e player jete parbe na
        if (!isWalkable(newRow, newCol)) {
            return;
        }

        // Movement successful hole position update + score barbe
        playerRow = newRow;
        playerCol = newCol;
        score += 10; // protyekbar move korle 10 point

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawScoreBar(g2d);
        drawMap(g2d);
        drawPlayer(g2d);
    }

    private void drawScoreBar(Graphics2D g2d) {
        g2d.setColor(new Color(25, 25, 25));
        g2d.fillRect(0, 0, PANEL_WIDTH, 50);

        g2d.setColor(Color.YELLOW);
        g2d.setFont(new Font("Arial", Font.BOLD, 22));
        g2d.drawString("Score: " + score, 15, 33);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.PLAIN, 14));
        g2d.drawString("Move with WASD / Arrow Keys", PANEL_WIDTH - 250, 30);
    }

    private void drawMap(Graphics2D g2d) {
        int yOffset = 50; // score bar er niche theke map suru

        for (int r = 0; r < GRID_ROWS; r++) {
            for (int c = 0; c < GRID_COLS; c++) {
                int x = c * TILE_SIZE;
                int y = yOffset + r * TILE_SIZE;
                int detail = tileDetail[r][c];

                switch (islandMap[r][c]) {
                    case DEEP_WATER:
                        g2d.setColor(new Color(16, 60, 140));
                        g2d.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                        drawWaterRipple(g2d, x, y, detail, new Color(40, 90, 180));
                        break;

                    case SHALLOW_WATER:
                        g2d.setColor(new Color(45, 130, 210));
                        g2d.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                        drawWaterRipple(g2d, x, y, detail, new Color(120, 190, 235));
                        break;

                    case SAND:
                        g2d.setColor(new Color(232, 197, 130));
                        g2d.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                        drawSpeckles(g2d, x, y, detail, new Color(200, 165, 100), 4);
                        break;

                    case GRASS:
                        g2d.setColor((detail % 2 == 0) ? new Color(72, 168, 76) : new Color(64, 158, 68));
                        g2d.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                        drawSpeckles(g2d, x, y, detail, new Color(50, 130, 55), 3);
                        break;

                    case FOREST:
                        g2d.setColor(new Color(58, 140, 62));
                        g2d.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                        drawTree(g2d, x, y, detail);
                        break;

                    case ROCK:
                        g2d.setColor(new Color(120, 115, 112));
                        g2d.fillRect(x, y, TILE_SIZE, TILE_SIZE);
                        drawRockDetail(g2d, x, y, detail);
                        break;
                }

                g2d.setColor(new Color(0, 0, 0, 35));
                g2d.drawRect(x, y, TILE_SIZE, TILE_SIZE);
            }
        }
    }

    private void drawWaterRipple(Graphics2D g2d, int x, int y, int detail, Color rippleColor) {
        g2d.setColor(rippleColor);
        int wy = y + 10 + (detail % 3) * 10;
        g2d.drawArc(x + 6, wy, 14, 6, 0, 180);
        g2d.drawArc(x + 22, wy + 8, 14, 6, 0, 180);
    }

    private void drawSpeckles(Graphics2D g2d, int x, int y, int detail, Color speckleColor, int count) {
        g2d.setColor(speckleColor);
        java.util.Random r = new java.util.Random(detail + x * 31 + y * 17);
        for (int i = 0; i < count; i++) {
            int sx = x + 4 + r.nextInt(TILE_SIZE - 8);
            int sy = y + 4 + r.nextInt(TILE_SIZE - 8);
            g2d.fillOval(sx, sy, 3, 3);
        }
    }

    private void drawTree(Graphics2D g2d, int x, int y, int detail) {
        int cx = x + TILE_SIZE / 2;
        int trunkW = 6;
        int trunkH = 10;

        // trunk
        g2d.setColor(new Color(90, 60, 30));
        g2d.fillRect(cx - trunkW / 2, y + TILE_SIZE - trunkH - 4, trunkW, trunkH);

        // canopy (two overlapping circles for a fuller look)
        Color leaf = (detail % 2 == 0) ? new Color(30, 105, 40) : new Color(35, 120, 48);
        g2d.setColor(leaf);
        int canopySize = 26;
        g2d.fillOval(cx - canopySize / 2, y + 4, canopySize, canopySize);
        g2d.fillOval(cx - canopySize / 2 - 6, y + 12, canopySize - 8, canopySize - 8);
        g2d.fillOval(cx - canopySize / 2 + 6, y + 12, canopySize - 8, canopySize - 8);
    }

    private void drawRockDetail(Graphics2D g2d, int x, int y, int detail) {
        java.util.Random r = new java.util.Random(detail + x * 13 + y * 7);
        g2d.setColor(new Color(90, 86, 84));
        int boulders = 2 + r.nextInt(2);
        for (int i = 0; i < boulders; i++) {
            int bs = 10 + r.nextInt(10);
            int bx = x + 4 + r.nextInt(TILE_SIZE - bs - 8);
            int by = y + 4 + r.nextInt(TILE_SIZE - bs - 8);
            g2d.fillOval(bx, by, bs, bs);
            g2d.setColor(new Color(150, 145, 140));
            g2d.fillOval(bx + 2, by + 2, bs / 3, bs / 3);
            g2d.setColor(new Color(90, 86, 84));
        }
    }

    private void drawPlayer(Graphics2D g2d) {
        int yOffset = 50;
        int x = playerCol * TILE_SIZE;
        int y = yOffset + playerRow * TILE_SIZE;

        g2d.setColor(Color.RED);
        int pad = 8;
        g2d.fillOval(x + pad, y + pad, TILE_SIZE - pad * 2, TILE_SIZE - pad * 2);

        g2d.setColor(Color.BLACK);
        g2d.drawOval(x + pad, y + pad, TILE_SIZE - pad * 2, TILE_SIZE - pad * 2);
    }
}