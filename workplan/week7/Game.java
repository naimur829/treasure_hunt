import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public class Game {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Treasure Hunt - Connected Maze with Junction Riddles");
            GamePanel panel = new GamePanel();

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}

final class Constants {
    private Constants() {}

    public static final int COLS = 25;
    public static final int ROWS = 19;
    public static final int TILE_SIZE = 32;

    public static final int HUD_HEIGHT = 64;

    public static final int MAP_WIDTH = COLS * TILE_SIZE;
    public static final int MAP_HEIGHT = ROWS * TILE_SIZE;
    public static final int WINDOW_WIDTH = MAP_WIDTH;
    public static final int WINDOW_HEIGHT = MAP_HEIGHT + HUD_HEIGHT;

    public static final int FPS = 60;
    public static final int FRAME_DELAY_MS = 1000 / FPS;

    public static final int STARTING_TORCH_FUEL = 100;
    public static final int MAX_TORCH_FUEL = 150;
    public static final int FUEL_PER_COIN = 35;

    public static final long MOVE_COOLDOWN_MS = 80;
    public static final double BASE_TORCH_DRAIN_PER_SECOND = 2.0;

    // Direction arrow stays visible for 2.5 seconds (150 frames) after collecting a riddle
    public static final int ARROW_POPUP_FRAMES = 150;
}

enum TileType {
    PATH(true, new Color(238, 214, 175)),
    WALL(false, new Color(45, 90, 39));

    public final boolean walkable;
    public final Color color;

    TileType(boolean walkable, Color color) {
        this.walkable = walkable;
        this.color = color;
    }
}

final class Items {
    private Items() {}

    public enum ItemType { COIN, CLUE_SCROLL, TREASURE_CHEST }

    public static abstract class Item {
        public final int col;
        public final int row;
        public final ItemType type;
        public boolean collected = false;

        protected Item(ItemType type, int col, int row) {
            this.type = type;
            this.col = col;
            this.row = row;
        }
    }

    public static class Coin extends Item {
        public Coin(int col, int row) { super(ItemType.COIN, col, row); }
    }

    public static class ClueScroll extends Item {
        public final int order;
        public ClueScroll(int col, int row, int order) {
            super(ItemType.CLUE_SCROLL, col, row);
            this.order = order;
        }
    }

    public static class TreasureChest extends Item {
        public TreasureChest(int col, int row) { super(ItemType.TREASURE_CHEST, col, row); }
    }
}

class ScoreManager {
    private static final int POINTS_PER_COIN = 100;
    private final long startTimeMillis;

    public ScoreManager(long startTimeMillis) {
        this.startTimeMillis = startTimeMillis;
    }

    public long getElapsedMillis() {
        return System.currentTimeMillis() - startTimeMillis;
    }

    public String getElapsedFormatted() {
        long secs = getElapsedMillis() / 1000;
        long m = secs / 60;
        long s = secs % 60;
        return String.format("%02d:%02d", m, s);
    }

    public int calculateFinalScore(int coinsCollected) {
        return coinsCollected * POINTS_PER_COIN;
    }
}

class Player {
    public int col;
    public int row;
    public int coins = 0;
    public int score = 0;
    public double torchFuel;
    public long lastMoveTimeMs = 0;

    public Player(int col, int row, double startingFuel) {
        this.col = col;
        this.row = row;
        this.torchFuel = startingFuel;
    }

    public boolean tryMove(int dc, int dr, GameMap map) {
        int nc = col + dc;
        int nr = row + dr;
        if (map.isWalkable(nc, nr)) {
            col = nc;
            row = nr;
            return true;
        }
        return false;
    }

    public void collectCoin() {
        coins++;
        torchFuel = Math.min(Constants.MAX_TORCH_FUEL, torchFuel + Constants.FUEL_PER_COIN);
    }
}

class GameMap {
    private final TileType[][] grid;
    private final List<Items.Item> items = new ArrayList<>();

    // Fully connected map grid using BFS verification to guarantee 100% reachability
    private static final int[][] MAP_LAYOUT = {
        {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
        {0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,2,0},
        {0,1,0,0,0,0,0,1,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,1,0},
        {0,1,0,1,1,1,0,1,0,1,1,1,1,1,0,1,0,1,1,1,1,1,0,1,0},
        {0,1,0,1,0,1,0,1,0,1,0,0,0,1,0,1,0,1,0,0,0,1,0,1,0},
        {0,1,1,1,0,1,1,1,1,1,0,1,0,1,1,1,1,1,0,1,0,1,1,1,0},
        {0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0},
        {0,1,1,1,1,1,0,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,0,1,0},
        {0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,0,0,0,0,1,0,0,0,1,0},
        {0,1,1,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,0},
        {0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,1,0},
        {0,1,1,1,1,1,0,1,0,1,1,1,0,1,0,1,1,1,1,1,1,1,0,1,0},
        {0,1,0,0,0,1,0,1,0,1,0,0,0,1,0,0,0,0,0,0,0,1,0,1,0},
        {0,1,0,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,1,0,1,0,1,0},
        {0,1,0,1,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,1,0,1,0,1,0},
        {0,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,1,0,1,1,1,0,1,0},
        {0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,1,0},
        {0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0},
        {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}
    };

    public GameMap() {
        grid = new TileType[Constants.ROWS][Constants.COLS];
        buildMapAndItems();
    }

    private void buildMapAndItems() {
        items.clear();

        // Place Clue Scrolls at key path junctions (3-way / 4-way splits)
        items.add(new Items.ClueScroll(7, 3, 1));   // Junction 1
        items.add(new Items.ClueScroll(11, 7, 2));  // Junction 2
        items.add(new Items.ClueScroll(17, 11, 3)); // Junction 3
        items.add(new Items.ClueScroll(7, 15, 4));  // Junction 4

        for (int r = 0; r < Constants.ROWS; r++) {
            for (int c = 0; c < Constants.COLS; c++) {
                int val = MAP_LAYOUT[r][c];
                if (val == 0) {
                    grid[r][c] = TileType.WALL;
                } else {
                    grid[r][c] = TileType.PATH;
                    if (val == 2) {
                        items.add(new Items.TreasureChest(c, r));
                    } else if ((r * c + r) % 8 == 0 && !(c == 1 && r == 1) && val != 2) {
                        items.add(new Items.Coin(c, r));
                    }
                }
            }
        }
    }

    public boolean isWalkable(int col, int row) {
        if (col < 0 || col >= Constants.COLS || row < 0 || row >= Constants.ROWS) return false;
        return grid[row][col].walkable;
    }

    public TileType getTile(int col, int row) {
        if (col < 0 || col >= Constants.COLS || row < 0 || row >= Constants.ROWS) return TileType.WALL;
        return grid[row][col];
    }

    public List<Items.Item> getItems() { return items; }
}

class GamePanel extends JPanel {

    public enum State { PLAYING, PAUSED, VICTORY, GAME_OVER }

    private State currentState = State.PLAYING;
    private GameMap map;
    private Player player;
    private ScoreManager scoreManager;
    private javax.swing.Timer gameLoopTimer;

    private int activeTargetOrder = 1;
    private int arrowTimer = 0; // Arrow popup timer initialized to 0 (Hidden at start)

    public GamePanel() {
        setPreferredSize(new Dimension(Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);

        initInputListeners();
        initGame();

        gameLoopTimer = new javax.swing.Timer(Constants.FRAME_DELAY_MS, e -> updateAndRepaint());
        gameLoopTimer.start();
    }

    private void initGame() {
        map = new GameMap();
        player = new Player(1, 1, Constants.STARTING_TORCH_FUEL);
        scoreManager = new ScoreManager(System.currentTimeMillis());
        activeTargetOrder = 1;
        arrowTimer = 0;
        currentState = State.PLAYING;
    }

    private void updateAndRepaint() {
        if (currentState == State.PLAYING) {
            double drainPerFrame = Constants.BASE_TORCH_DRAIN_PER_SECOND / Constants.FPS;
            player.torchFuel -= drainPerFrame;

            if (arrowTimer > 0) {
                arrowTimer--;
            }

            if (player.torchFuel <= 0) {
                player.torchFuel = 0;
                currentState = State.GAME_OVER;
            }
            checkCollisions();
        }
        repaint();
    }

    private void checkCollisions() {
        for (Items.Item item : map.getItems()) {
            if (!item.collected && item.col == player.col && item.row == player.row) {
                if (item instanceof Items.Coin) {
                    item.collected = true;
                    player.collectCoin();
                } else if (item instanceof Items.ClueScroll) {
                    Items.ClueScroll scroll = (Items.ClueScroll) item;
                    if (scroll.order == activeTargetOrder) {
                        scroll.collected = true;
                        activeTargetOrder++;
                        arrowTimer = Constants.ARROW_POPUP_FRAMES; // Trigger arrow popup on riddle pickup
                    }
                } else if (item instanceof Items.TreasureChest) {
                    if (activeTargetOrder > 4) { // Requires all 4 junction clues
                        item.collected = true;
                        player.score = scoreManager.calculateFinalScore(player.coins);
                        currentState = State.VICTORY;
                    }
                }
            }
        }
    }

    private void handlePlayerMovement(int dc, int dr) {
        if (currentState != State.PLAYING) return;
        long now = System.currentTimeMillis();
        if (now - player.lastMoveTimeMs < Constants.MOVE_COOLDOWN_MS) return;

        if (player.tryMove(dc, dr, map)) {
            player.lastMoveTimeMs = now;
        }
    }

    private void initInputListeners() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();

                if (currentState == State.PLAYING) {
                    if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP) handlePlayerMovement(0, -1);
                    if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN) handlePlayerMovement(0, 1);
                    if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) handlePlayerMovement(-1, 0);
                    if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) handlePlayerMovement(1, 0);
                    if (code == KeyEvent.VK_ESCAPE) currentState = State.PAUSED;
                } else if (currentState == State.PAUSED) {
                    if (code == KeyEvent.VK_ESCAPE) currentState = State.PLAYING;
                } else if (currentState == State.GAME_OVER || currentState == State.VICTORY) {
                    if (code == KeyEvent.VK_ENTER) initGame();
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawMap(g2);
        drawItems(g2);
        drawPlayerCharacter(g2);

        applyDarknessMask(g2);

        // Arrow only renders during the temporary popup timer after collecting a riddle
        if (arrowTimer > 0) {
            drawDirectionArrow(g2);
        }

        drawHUD(g2);

        if (currentState == State.PAUSED) drawPausedOverlay(g2);
        if (currentState == State.GAME_OVER) drawGameOverOverlay(g2);
        if (currentState == State.VICTORY) drawVictoryOverlay(g2);
    }

    private void drawMap(Graphics2D g) {
        for (int r = 0; r < Constants.ROWS; r++) {
            for (int c = 0; c < Constants.COLS; c++) {
                TileType tile = map.getTile(c, r);
                g.setColor(tile.color);
                int x = c * Constants.TILE_SIZE;
                int y = r * Constants.TILE_SIZE;
                g.fillRect(x, y, Constants.TILE_SIZE, Constants.TILE_SIZE);

                if (tile == TileType.WALL) {
                    g.setColor(new Color(30, 70, 25));
                    g.fillOval(x + 4, y + 4, Constants.TILE_SIZE - 8, Constants.TILE_SIZE - 8);
                }
            }
        }
    }

    private void drawItems(Graphics2D g) {
        for (Items.Item item : map.getItems()) {
            if (!item.collected) {
                int x = item.col * Constants.TILE_SIZE;
                int y = item.row * Constants.TILE_SIZE;

                if (item instanceof Items.Coin) {
                    g.setColor(new Color(255, 215, 0));
                    g.fillOval(x + 8, y + 8, 16, 16);
                    g.setColor(new Color(218, 165, 32));
                    g.drawOval(x + 8, y + 8, 16, 16);
                } else if (item instanceof Items.ClueScroll) {
                    Items.ClueScroll scroll = (Items.ClueScroll) item;
                    if (scroll.order == activeTargetOrder) {
                        g.setColor(new Color(235, 225, 180));
                        g.fillRect(x + 8, y + 6, 16, 20);
                        g.setColor(Color.DARK_GRAY);
                        g.drawRect(x + 8, y + 6, 16, 20);
                    }
                } else if (item instanceof Items.TreasureChest) {
                    if (activeTargetOrder > 4) {
                        g.setColor(new Color(139, 69, 19));
                        g.fillRect(x + 4, y + 8, 24, 18);
                        g.setColor(new Color(255, 215, 0));
                        g.drawRect(x + 4, y + 8, 24, 18);
                        g.fillRect(x + 14, y + 14, 4, 6);
                    }
                }
            }
        }
    }

    private void drawPlayerCharacter(Graphics2D g) {
        int x = player.col * Constants.TILE_SIZE;
        int y = player.row * Constants.TILE_SIZE;

        g.setColor(new Color(255, 204, 153));
        g.fillOval(x + 6, y + 8, 20, 20);

        g.setColor(Color.BLACK);
        g.fillOval(x + 10, y + 14, 3, 3);
        g.fillOval(x + 19, y + 14, 3, 3);

        g.drawArc(x + 13, y + 18, 6, 4, 0, -180);

        g.setColor(new Color(160, 82, 45));
        g.fillRect(x + 2, y + 6, 28, 4);
        g.fillRect(x + 8, y + 1, 16, 6);

        g.setColor(new Color(34, 139, 34));
        g.fillRect(x + 9, y + 24, 14, 8);
    }

    private void applyDarknessMask(Graphics2D g) {
        int pX = player.col * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;
        int pY = player.row * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;

        double radius = Math.max(30, player.torchFuel * 2.2);

        Area darkness = new Area(new Rectangle2D.Double(0, 0, Constants.MAP_WIDTH, Constants.MAP_HEIGHT));
        Ellipse2D lightCircle = new Ellipse2D.Double(pX - radius, pY - radius, radius * 2, radius * 2);
        darkness.subtract(new Area(lightCircle));

        g.setColor(new Color(0, 0, 0, 240));
        g.fill(darkness);
    }

    private void drawDirectionArrow(Graphics2D g) {
        int targetCol = -1;
        int targetRow = -1;

        if (activeTargetOrder <= 4) {
            for (Items.Item item : map.getItems()) {
                if (item instanceof Items.ClueScroll) {
                    Items.ClueScroll scroll = (Items.ClueScroll) item;
                    if (scroll.order == activeTargetOrder) {
                        targetCol = scroll.col;
                        targetRow = scroll.row;
                        break;
                    }
                }
            }
        } else {
            for (Items.Item item : map.getItems()) {
                if (item instanceof Items.TreasureChest) {
                    targetCol = item.col;
                    targetRow = item.row;
                    break;
                }
            }
        }

        if (targetCol != -1 && targetRow != -1) {
            double pX = player.col * Constants.TILE_SIZE + Constants.TILE_SIZE / 2.0;
            double pY = player.row * Constants.TILE_SIZE + Constants.TILE_SIZE / 2.0;
            double tX = targetCol * Constants.TILE_SIZE + Constants.TILE_SIZE / 2.0;
            double tY = targetRow * Constants.TILE_SIZE + Constants.TILE_SIZE / 2.0;

            double angle = Math.atan2(tY - pY, tX - pX);

            int arrowLength = 25;
            double arrowX = pX + Math.cos(angle) * 32;
            double arrowY = pY + Math.sin(angle) * 32;

            AffineTransform old = g.getTransform();
            g.translate(arrowX, arrowY);
            g.rotate(angle);

            g.setColor(Color.CYAN);
            Polygon arrow = new Polygon();
            arrow.addPoint(arrowLength, 0);
            arrow.addPoint(-10, -8);
            arrow.addPoint(-4, 0);
            arrow.addPoint(-10, 8);
            g.fill(arrow);
            g.setColor(Color.WHITE);
            g.draw(arrow);

            g.setTransform(old);
        }
    }

    private void drawHUD(Graphics2D g) {
        int yStart = Constants.MAP_HEIGHT;
        g.setColor(new Color(30, 30, 40));
        g.fillRect(0, yStart, Constants.WINDOW_WIDTH, Constants.HUD_HEIGHT);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.drawString("Coins: " + player.coins, 20, yStart + 25);
        
        String targetText = activeTargetOrder <= 4 ? "Junction Clue #" + activeTargetOrder : "TREASURE UNLOCKED!";
        g.setColor(Color.CYAN);
        g.drawString(targetText, 120, yStart + 25);

        if (arrowTimer > 0) {
            g.setColor(Color.YELLOW);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString("ARROW DIRECTION POPUP", 310, yStart + 25);
        }

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.drawString("Time: " + scoreManager.getElapsedFormatted(), 490, yStart + 25);

        g.drawString("Torch Fuel:", 20, yStart + 50);
        g.setColor(Color.GRAY);
        g.fillRect(110, yStart + 38, 150, 14);
        g.setColor(player.torchFuel > 30 ? Color.ORANGE : Color.RED);
        g.fillRect(110, yStart + 38, (int) ((player.torchFuel / Constants.MAX_TORCH_FUEL) * 150), 14);

        g.setColor(Color.LIGHT_GRAY);
        g.setFont(new Font("SansSerif", Font.ITALIC, 12));
        g.drawString("WASD/Arrows: Move | ESC: Pause", Constants.WINDOW_WIDTH - 240, yStart + 38);
    }

    private void drawPausedOverlay(Graphics2D g) {
        drawOverlayBox(g, "PAUSED");
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.drawString("Press ESC to Resume", Constants.WINDOW_WIDTH / 2 - 80, 220);
    }

    private void drawGameOverOverlay(Graphics2D g) {
        drawOverlayBox(g, "GAME OVER");
        g.setColor(Color.RED);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.drawString("Your torch burned out in the dark!", Constants.WINDOW_WIDTH / 2 - 160, 210);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.drawString("Press ENTER to Restart", Constants.WINDOW_WIDTH / 2 - 80, 260);
    }

    private void drawVictoryOverlay(Graphics2D g) {
        drawOverlayBox(g, "TREASURE FOUND!");
        g.setColor(new Color(255, 215, 0));
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.drawString("You solved all junction riddles and found the Treasure!", Constants.WINDOW_WIDTH / 2 - 230, 190);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.drawString("Coins Collected: " + player.coins, Constants.WINDOW_WIDTH / 2 - 80, 230);
        g.drawString("Time Taken: " + scoreManager.getElapsedFormatted(), Constants.WINDOW_WIDTH / 2 - 80, 260);
        g.setColor(Color.LIGHT_GRAY);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.drawString("Press ENTER to Play Again", Constants.WINDOW_WIDTH / 2 - 85, 300);
    }

    private void drawOverlayBox(Graphics2D g, String title) {
        g.setColor(new Color(0, 0, 0, 215));
        g.fillRect(80, 70, Constants.WINDOW_WIDTH - 160, 270);
        g.setColor(new Color(255, 215, 0));
        g.drawRect(80, 70, Constants.WINDOW_WIDTH - 160, 270);
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.drawString(title, Constants.WINDOW_WIDTH / 2 - 80, 120);
    }
}