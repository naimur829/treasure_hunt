import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import java.util.List;

public class Game {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Treasure Hunt - Multi-Level Maze Adventure");
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

    public static final int HUD_HEIGHT = 80;

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

    public static final int ARROW_POPUP_FRAMES = 150;
}

class Storage {
    private static final String SAVE_FILE = "treasure_hunt_save.dat";

    public static synchronized void saveHighScore(int highScore) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(SAVE_FILE))) {
            oos.writeInt(highScore);
        } catch (IOException e) {
            System.err.println("Error saving high score data: " + e.getMessage());
        }
    }

    public static synchronized int loadHighScore() {
        File file = new File(SAVE_FILE);
        if (!file.exists()) return 0;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return ois.readInt();
        } catch (IOException e) {
            System.err.println("Error loading high score data: " + e.getMessage());
            return 0;
        }
    }
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

    public static abstract class Item implements Serializable {
        private static final long serialVersionUID = 1L;
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
        private static final long serialVersionUID = 1L;
        public Coin(int col, int row) { super(ItemType.COIN, col, row); }
    }

    public static class ClueScroll extends Item {
        private static final long serialVersionUID = 1L;
        public final int order;
        public ClueScroll(int col, int row, int order) {
            super(ItemType.CLUE_SCROLL, col, row);
            this.order = order;
        }
    }

    public static class TreasureChest extends Item {
        private static final long serialVersionUID = 1L;
        public TreasureChest(int col, int row) { super(ItemType.TREASURE_CHEST, col, row); }
    }
}

class ScoreManager {
    private static final int POINTS_PER_COIN = 100;
    private final long startTimeMillis;
    private long stoppedElapsedMillis = -1;

    public ScoreManager(long startTimeMillis) {
        this.startTimeMillis = startTimeMillis;
    }

    public void stopTimer() {
        if (stoppedElapsedMillis < 0) {
            stoppedElapsedMillis = System.currentTimeMillis() - startTimeMillis;
        }
    }

    public long getElapsedMillis() {
        if (stoppedElapsedMillis >= 0) {
            return stoppedElapsedMillis;
        }
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

    private static final int[][] MAP_LEVEL_1 = {
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

    private static final int[][] MAP_LEVEL_2 = {
        {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
        {0,1,0,1,1,1,1,1,0,1,1,1,1,1,0,1,1,1,1,1,0,1,1,2,0},
        {0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,1,0},
        {0,1,1,1,0,1,1,1,1,1,0,1,1,1,1,1,1,1,0,1,1,1,0,1,0},
        {0,0,0,1,0,1,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,1,0},
        {0,1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,0,1,1,1,1,1,1,1,0},
        {0,1,0,0,0,0,0,1,0,1,0,0,0,0,0,1,0,1,0,0,0,0,0,1,0},
        {0,1,1,1,1,1,0,1,0,1,1,1,1,1,0,1,0,1,1,1,1,1,0,1,0},
        {0,0,0,0,0,1,0,1,0,0,0,0,0,1,0,0,0,0,0,0,0,1,0,1,0},
        {0,1,1,1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,0,1,1,1,0},
        {0,1,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,1,0,0,0,1,0},
        {0,1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,0,1,1,1,1,1,0},
        {0,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,1,0,0,0,0,0,1,0},
        {0,1,1,1,0,1,1,1,0,1,1,1,1,1,0,1,1,1,0,1,1,1,1,1,0},
        {0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,1,0},
        {0,1,0,1,1,1,1,1,1,1,1,1,0,1,1,1,1,1,1,1,1,1,0,1,0},
        {0,1,0,0,0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0,1,0,1,0},
        {0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0},
        {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}
    };

    public GameMap(int level) {
        grid = new TileType[Constants.ROWS][Constants.COLS];
        buildMapAndItems(level);
    }

    private void buildMapAndItems(int level) {
        items.clear();
        int[][] layout = (level == 2) ? MAP_LEVEL_2 : MAP_LEVEL_1;

        if (level == 1) {
            items.add(new Items.ClueScroll(7, 3, 1));
            items.add(new Items.ClueScroll(11, 7, 2));
            items.add(new Items.ClueScroll(17, 11, 3));
            items.add(new Items.ClueScroll(7, 15, 4));
        } else {
            items.add(new Items.ClueScroll(5, 3, 1));
            items.add(new Items.ClueScroll(13, 5, 2));
            items.add(new Items.ClueScroll(7, 11, 3));
            items.add(new Items.ClueScroll(17, 13, 4));
        }

        for (int r = 0; r < Constants.ROWS; r++) {
            for (int c = 0; c < Constants.COLS; c++) {
                int val = layout[r][c];
                if (val == 0) {
                    grid[r][c] = TileType.WALL;
                } else {
                    grid[r][c] = TileType.PATH;
                    if (val == 2) {
                        items.add(new Items.TreasureChest(c, r));
                    } else if ((r * c + r) % 7 == 0 && !(c == 1 && r == 1) && val != 2) {
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

    public enum State { PLAYING, PAUSED, LEVEL_COMPLETE, VICTORY, GAME_OVER }

    private State currentState = State.PLAYING;
    private GameMap map;
    private Player player;
    private ScoreManager scoreManager;
    private javax.swing.Timer gameLoopTimer;

    private int currentLevel = 1;
    private int activeTargetOrder = 1;
    private int arrowTimer = 0;
    private int highScore = 0;
    private int totalAccumulatedCoins = 0;

    private final Rectangle btnUp = new Rectangle(Constants.WINDOW_WIDTH - 130, Constants.MAP_HEIGHT + 10, 40, 30);
    private final Rectangle btnDown = new Rectangle(Constants.WINDOW_WIDTH - 130, Constants.MAP_HEIGHT + 45, 40, 30);
    private final Rectangle btnLeft = new Rectangle(Constants.WINDOW_WIDTH - 175, Constants.MAP_HEIGHT + 27, 40, 30);
    private final Rectangle btnRight = new Rectangle(Constants.WINDOW_WIDTH - 85, Constants.MAP_HEIGHT + 27, 40, 30);

    public GamePanel() {
        setPreferredSize(new Dimension(Constants.WINDOW_WIDTH, Constants.WINDOW_HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);

        highScore = Storage.loadHighScore();

        initInputListeners();
        initGame(1, 0, Constants.STARTING_TORCH_FUEL);

        gameLoopTimer = new javax.swing.Timer(Constants.FRAME_DELAY_MS, e -> updateAndRepaint());
        gameLoopTimer.start();
    }

    private void initGame(int level, int carriedCoins, double carriedTorch) {
        currentLevel = level;
        totalAccumulatedCoins = carriedCoins;
        map = new GameMap(currentLevel);
        player = new Player(1, 1, Math.min(Constants.MAX_TORCH_FUEL, carriedTorch));
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
                scoreManager.stopTimer(); // Freeze timer immediately on Game Over
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
                        arrowTimer = Constants.ARROW_POPUP_FRAMES;
                    }
                } else if (item instanceof Items.TreasureChest) {
                    if (activeTargetOrder > 4) {
                        item.collected = true;
                        int currentCoinsTotal = totalAccumulatedCoins + player.coins;
                        
                        if (currentLevel == 1) {
                            scoreManager.stopTimer(); // Freeze timer on Level Complete transition
                            currentState = State.LEVEL_COMPLETE;
                        } else {
                            player.score = scoreManager.calculateFinalScore(currentCoinsTotal);
                            if (player.score > highScore) {
                                highScore = player.score;
                                Storage.saveHighScore(highScore);
                            }
                            scoreManager.stopTimer(); // Freeze timer on final Victory
                            currentState = State.VICTORY;
                        }
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
                } else if (currentState == State.LEVEL_COMPLETE) {
                    if (code == KeyEvent.VK_ENTER) {
                        initGame(2, totalAccumulatedCoins + player.coins, player.torchFuel + 30);
                    }
                } else if (currentState == State.GAME_OVER || currentState == State.VICTORY) {
                    if (code == KeyEvent.VK_ENTER) {
                        initGame(1, 0, Constants.STARTING_TORCH_FUEL);
                    }
                }
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Point p = e.getPoint();
                if (currentState == State.PLAYING) {
                    if (btnUp.contains(p)) handlePlayerMovement(0, -1);
                    else if (btnDown.contains(p)) handlePlayerMovement(0, 1);
                    else if (btnLeft.contains(p)) handlePlayerMovement(-1, 0);
                    else if (btnRight.contains(p)) handlePlayerMovement(1, 0);
                } else if (currentState == State.LEVEL_COMPLETE) {
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        initGame(2, totalAccumulatedCoins + player.coins, player.torchFuel + 30);
                    }
                } else if (currentState == State.GAME_OVER || currentState == State.VICTORY) {
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        initGame(1, 0, Constants.STARTING_TORCH_FUEL);
                    }
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

        if (arrowTimer > 0) {
            drawDirectionArrow(g2);
        }

        drawHUD(g2);

        if (currentState == State.PAUSED) drawPausedOverlay(g2);
        if (currentState == State.LEVEL_COMPLETE) drawLevelCompleteOverlay(g2);
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
                    g.setColor(currentLevel == 1 ? new Color(30, 70, 25) : new Color(70, 30, 30));
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

        g.setColor(currentLevel == 1 ? new Color(34, 139, 34) : new Color(139, 0, 0));
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
        g.drawString("Level: " + currentLevel + " | Coins: " + (totalAccumulatedCoins + player.coins), 20, yStart + 25);
        
        String targetText = activeTargetOrder <= 4 ? "Clue #" + activeTargetOrder : "CHEST UNLOCKED!";
        g.setColor(Color.CYAN);
        g.drawString(targetText, 215, yStart + 25);

        g.setColor(Color.YELLOW);
        g.drawString("High: " + highScore, 360, yStart + 25);

        g.setColor(Color.WHITE);
        g.drawString("Time: " + scoreManager.getElapsedFormatted(), 465, yStart + 25);

        g.drawString("Torch:", 20, yStart + 60);
        g.setColor(Color.GRAY);
        g.fillRect(70, yStart + 48, 130, 14);
        g.setColor(player.torchFuel > 30 ? Color.ORANGE : Color.RED);
        g.fillRect(70, yStart + 48, (int) ((player.torchFuel / Constants.MAX_TORCH_FUEL) * 130), 14);

        drawTouchPad(g);

        g.setColor(Color.LIGHT_GRAY);
        g.setFont(new Font("SansSerif", Font.ITALIC, 11));
        g.drawString("WASD/Arrows | ESC: Pause", 240, yStart + 62);
    }

    private void drawTouchPad(Graphics2D g) {
        g.setColor(new Color(60, 60, 80));
        g.fill3DRect(btnUp.x, btnUp.y, btnUp.width, btnUp.height, true);
        g.fill3DRect(btnDown.x, btnDown.y, btnDown.width, btnDown.height, true);
        g.fill3DRect(btnLeft.x, btnLeft.y, btnLeft.width, btnLeft.height, true);
        g.fill3DRect(btnRight.x, btnRight.y, btnRight.width, btnRight.height, true);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString("▲", btnUp.x + 13, btnUp.y + 20);
        g.drawString("▼", btnDown.x + 13, btnDown.y + 20);
        g.drawString("◀", btnLeft.x + 13, btnLeft.y + 20);
        g.drawString("▶", btnRight.x + 13, btnRight.y + 20);
    }

    private void drawPausedOverlay(Graphics2D g) {
        drawOverlayBox(g, "PAUSED");
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.drawString("Press ESC to Resume", Constants.WINDOW_WIDTH / 2 - 80, 220);
    }

    private void drawLevelCompleteOverlay(Graphics2D g) {
        drawOverlayBox(g, "LEVEL 1 CLEARED!");
        g.setColor(new Color(0, 255, 127));
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString("You found the chest and survived Level 1!", Constants.WINDOW_WIDTH / 2 - 170, 185);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.drawString("Carried Coins: " + (totalAccumulatedCoins + player.coins), Constants.WINDOW_WIDTH / 2 - 80, 220);
        g.drawString("Bonus Torch Added!", Constants.WINDOW_WIDTH / 2 - 80, 245);
        g.setColor(Color.YELLOW);
        g.drawString("Press ENTER or Click to Enter Level 2", Constants.WINDOW_WIDTH / 2 - 130, 290);
    }

    private void drawGameOverOverlay(Graphics2D g) {
        drawOverlayBox(g, "GAME OVER");
        g.setColor(Color.RED);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.drawString("Your torch burned out in the dark!", Constants.WINDOW_WIDTH / 2 - 160, 210);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.drawString("Press ENTER or Click to Restart", Constants.WINDOW_WIDTH / 2 - 110, 260);
    }

    private void drawVictoryOverlay(Graphics2D g) {
        drawOverlayBox(g, "VICTORY! ALL LEVELS CLEARED");
        g.setColor(new Color(255, 215, 0));
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString("You successfully conquered both mazes!", Constants.WINDOW_WIDTH / 2 - 170, 180);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g.drawString("Total Coins: " + (totalAccumulatedCoins + player.coins), Constants.WINDOW_WIDTH / 2 - 80, 215);
        g.drawString("Final Score: " + player.score, Constants.WINDOW_WIDTH / 2 - 80, 240);
        g.drawString("Total Time: " + scoreManager.getElapsedFormatted(), Constants.WINDOW_WIDTH / 2 - 80, 265);
        g.setColor(Color.LIGHT_GRAY);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.drawString("Press ENTER or Click to Play Again", Constants.WINDOW_WIDTH / 2 - 110, 310);
    }

    private void drawOverlayBox(Graphics2D g, String title) {
        g.setColor(new Color(0, 0, 0, 215));
        g.fillRect(80, 70, Constants.WINDOW_WIDTH - 160, 290);
        g.setColor(new Color(255, 215, 0));
        g.drawRect(80, 70, Constants.WINDOW_WIDTH - 160, 290);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.drawString(title, Constants.WINDOW_WIDTH / 2 - 110, 120);
    }
}