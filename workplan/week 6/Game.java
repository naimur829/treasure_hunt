import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public class Game {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Treasure Island - Weeks 1 to 6 Prototype");
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

    public static final int COLS = 46;
    public static final int ROWS = 30;
    public static final int TILE_SIZE = 20;

    public static final int HUD_HEIGHT = 64;

    public static final int MAP_WIDTH = COLS * TILE_SIZE;
    public static final int MAP_HEIGHT = ROWS * TILE_SIZE;
    public static final int WINDOW_WIDTH = MAP_WIDTH;
    public static final int WINDOW_HEIGHT = MAP_HEIGHT + HUD_HEIGHT;

    public static final int FPS = 60;
    public static final int FRAME_DELAY_MS = 1000 / FPS;

    public static final int STARTING_TORCH_FUEL = 100;
    public static final int MAX_TORCH_FUEL = 130;

    public static final int NUM_COINS = 16;
    public static final int NUM_CLUE_SCROLLS = 5;
    public static final int NUM_TORCH_FUEL_PICKUPS = 6;

    public static final long MOVE_COOLDOWN_MS = 100;
    public static final double BASE_TORCH_DRAIN_PER_SECOND = 1.5;
}

enum TileType {
    WATER(false),
    SAND(true),
    GRASS(true),
    ROCK(false),
    TREE(false);

    public final boolean walkable;

    TileType(boolean walkable) {
        this.walkable = walkable;
    }
}

class Riddle {
    public final String text;
    public final String hint;

    public Riddle(String text, String hint) {
        this.text = text;
        this.hint = hint;
    }
}

final class Items {
    private Items() {}

    public enum ItemType { COIN, CLUE_SCROLL, TREASURE_CHEST, TORCH_FUEL }

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

        public abstract Color color();
    }

    public static class Coin extends Item {
        public static final int VALUE = 10;
        public Coin(int col, int row) { super(ItemType.COIN, col, row); }
        public Color color() { return new Color(255, 215, 0); }
    }

    public static class ClueScroll extends Item {
        public final Riddle riddle;
        public ClueScroll(int col, int row, Riddle riddle) {
            super(ItemType.CLUE_SCROLL, col, row);
            this.riddle = riddle;
        }
        public Color color() { return new Color(235, 225, 180); }
    }

    public static class TorchFuelPickup extends Item {
        public static final int AMOUNT = 35;
        public TorchFuelPickup(int col, int row) { super(ItemType.TORCH_FUEL, col, row); }
        public Color color() { return new Color(255, 140, 0); }
    }

    public static class TreasureChest extends Item {
        public TreasureChest(int col, int row) { super(ItemType.TREASURE_CHEST, col, row); }
        public Color color() { return new Color(139, 90, 43); }
    }
}

class Player {
    public int col;
    public int row;
    public int coins = 0;
    public int score = 0;
    public double torchFuel;
    public final List<Riddle> collectedRiddles = new ArrayList<>();
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
        score += Items.Coin.VALUE;
    }

    public void collectScroll(Riddle riddle) {
        collectedRiddles.add(riddle);
        score += 50;
    }

    public void collectFuel(int amount, int maxFuel) {
        torchFuel = Math.min(maxFuel, torchFuel + amount);
    }
}

class GameMap {
    private final TileType[][] grid;
    private final List<Items.Item> items = new ArrayList<>();
    private final Random random = new Random();

    public GameMap() {
        grid = new TileType[Constants.ROWS][Constants.COLS];
        generateIsland();
    }

    private void generateIsland() {
        int centerX = Constants.COLS / 2;
        int centerY = Constants.ROWS / 2;
        double maxRadius = Math.min(Constants.COLS, Constants.ROWS) * 0.42;

        for (int r = 0; r < Constants.ROWS; r++) {
            for (int c = 0; c < Constants.COLS; c++) {
                double dist = Math.hypot(c - centerX, r - centerY);
                double noise = (random.nextDouble() - 0.5) * 3.5;

                if (dist + noise > maxRadius) {
                    grid[r][c] = TileType.WATER;
                } else if (dist + noise > maxRadius - 2.5) {
                    grid[r][c] = TileType.SAND;
                } else {
                    double feature = random.nextDouble();
                    if (feature < 0.65) grid[r][c] = TileType.GRASS;
                    else if (feature < 0.85) grid[r][c] = TileType.TREE;
                    else grid[r][c] = TileType.ROCK;
                }
            }
        }
    }

    public boolean isWalkable(int col, int row) {
        if (col < 0 || col >= Constants.COLS || row < 0 || row >= Constants.ROWS) return false;
        return grid[row][col].walkable;
    }

    public TileType getTile(int col, int row) {
        if (col < 0 || col >= Constants.COLS || row < 0 || row >= Constants.ROWS) return TileType.WATER;
        return grid[row][col];
    }

    public void populateItems() {
        items.clear();
        for (int i = 0; i < Constants.NUM_CLUE_SCROLLS; i++) {
            int[] pos = getRandomWalkableTile();
            items.add(new Items.ClueScroll(pos[0], pos[1], new Riddle("Clue Scroll #" + (i + 1), "Search near the terrain features.")));
        }
        for (int i = 0; i < Constants.NUM_COINS; i++) {
            int[] pos = getRandomWalkableTile();
            items.add(new Items.Coin(pos[0], pos[1]));
        }
        for (int i = 0; i < Constants.NUM_TORCH_FUEL_PICKUPS; i++) {
            int[] pos = getRandomWalkableTile();
            items.add(new Items.TorchFuelPickup(pos[0], pos[1]));
        }
        int[] chestPos = getRandomWalkableTile();
        items.add(new Items.TreasureChest(chestPos[0], chestPos[1]));
    }

    public int[] getRandomWalkableTile() {
        int c, r;
        do {
            c = random.nextInt(Constants.COLS);
            r = random.nextInt(Constants.ROWS);
        } while (!isWalkable(c, r));
        return new int[]{c, r};
    }

    public List<Items.Item> getItems() { return items; }
}

class GamePanel extends JPanel {

    public enum State { PLAYING, INVENTORY, GAME_OVER }

    private State currentState = State.PLAYING;
    private GameMap map;
    private Player player;
    private javax.swing.Timer gameLoopTimer;

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
        int[] startPos = map.getRandomWalkableTile();
        player = new Player(startPos[0], startPos[1], Constants.STARTING_TORCH_FUEL);
        map.populateItems();
        currentState = State.PLAYING;
    }

    private void updateAndRepaint() {
        if (currentState == State.PLAYING) {
            double drainPerFrame = Constants.BASE_TORCH_DRAIN_PER_SECOND / Constants.FPS;
            player.torchFuel -= drainPerFrame;

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
                item.collected = true;
                if (item instanceof Items.Coin) player.collectCoin();
                else if (item instanceof Items.ClueScroll) player.collectScroll(((Items.ClueScroll) item).riddle);
                else if (item instanceof Items.TorchFuelPickup) player.collectFuel(Items.TorchFuelPickup.AMOUNT, Constants.MAX_TORCH_FUEL);
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
                    
                    if (code == KeyEvent.VK_I) currentState = State.INVENTORY;
                } else if (currentState == State.INVENTORY) {
                    if (code == KeyEvent.VK_I || code == KeyEvent.VK_ESCAPE) currentState = State.PLAYING;
                } else if (currentState == State.GAME_OVER) {
                    if (code == KeyEvent.VK_ENTER) initGame();
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        drawMap(g2);
        drawItems(g2);
        drawPlayer(g2);
        
        applyDarknessMask(g2);
        
        drawHUD(g2);

        if (currentState == State.INVENTORY) drawInventoryOverlay(g2);
        if (currentState == State.GAME_OVER) drawGameOverOverlay(g2);
    }

    private void drawMap(Graphics2D g) {
        for (int r = 0; r < Constants.ROWS; r++) {
            for (int c = 0; c < Constants.COLS; c++) {
                TileType tile = map.getTile(c, r);
                switch (tile) {
                    case WATER -> g.setColor(new Color(30, 144, 255));
                    case SAND -> g.setColor(new Color(238, 214, 175));
                    case GRASS -> g.setColor(new Color(34, 139, 34));
                    case ROCK -> g.setColor(new Color(112, 128, 144));
                    case TREE -> g.setColor(new Color(0, 100, 0));
                }
                g.fillRect(c * Constants.TILE_SIZE, r * Constants.TILE_SIZE, Constants.TILE_SIZE, Constants.TILE_SIZE);
            }
        }
    }

    private void drawItems(Graphics2D g) {
        for (Items.Item item : map.getItems()) {
            if (!item.collected) {
                g.setColor(item.color());
                int x = item.col * Constants.TILE_SIZE;
                int y = item.row * Constants.TILE_SIZE;
                g.fillOval(x + 3, y + 3, Constants.TILE_SIZE - 6, Constants.TILE_SIZE - 6);
            }
        }
    }

    private void drawPlayer(Graphics2D g) {
        g.setColor(Color.RED);
        g.fillOval(player.col * Constants.TILE_SIZE + 2, player.row * Constants.TILE_SIZE + 2, Constants.TILE_SIZE - 4, Constants.TILE_SIZE - 4);
    }

    private void applyDarknessMask(Graphics2D g) {
        int pX = player.col * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;
        int pY = player.row * Constants.TILE_SIZE + Constants.TILE_SIZE / 2;
        double radius = Math.max(20, player.torchFuel * 2.2);

        Area darkness = new Area(new Rectangle2D.Double(0, 0, Constants.MAP_WIDTH, Constants.MAP_HEIGHT));
        Ellipse2D lightCircle = new Ellipse2D.Double(pX - radius, pY - radius, radius * 2, radius * 2);
        darkness.subtract(new Area(lightCircle));

        g.setColor(new Color(0, 0, 0, 235));
        g.fill(darkness);
    }

    private void drawHUD(Graphics2D g) {
        int yStart = Constants.MAP_HEIGHT;
        g.setColor(new Color(30, 30, 40));
        g.fillRect(0, yStart, Constants.WINDOW_WIDTH, Constants.HUD_HEIGHT);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.drawString("Coins: " + player.coins, 20, yStart + 25);
        g.drawString("Scrolls: " + player.collectedRiddles.size() + "/" + Constants.NUM_CLUE_SCROLLS, 140, yStart + 25);
        g.drawString("Score: " + player.score, 280, yStart + 25);

        g.drawString("Torch Fuel:", 20, yStart + 50);
        g.setColor(Color.GRAY);
        g.fillRect(110, yStart + 38, 150, 14);
        g.setColor(player.torchFuel > 25 ? Color.ORANGE : Color.RED);
        g.fillRect(110, yStart + 38, (int) ((player.torchFuel / Constants.MAX_TORCH_FUEL) * 150), 14);

        g.setColor(Color.LIGHT_GRAY);
        g.setFont(new Font("SansSerif", Font.ITALIC, 12));
        g.drawString("Press 'I' for Inventory", Constants.WINDOW_WIDTH - 160, yStart + 38);
    }

    private void drawInventoryOverlay(Graphics2D g) {
        drawOverlayBox(g, "INVENTORY & COLLECTED SCROLLS");
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        int y = 190;
        for (int i = 0; i < player.collectedRiddles.size(); i++) {
            Riddle r = player.collectedRiddles.get(i);
            g.setColor(Color.YELLOW);
            g.drawString("Scroll " + (i + 1) + ": " + r.text, 180, y);
            y += 30;
        }
        if (player.collectedRiddles.isEmpty()) {
            g.setColor(Color.GRAY);
            g.drawString("No clue scrolls collected yet.", 180, 200);
        }
        g.setColor(Color.WHITE);
        g.drawString("Press 'I' or 'ESC' to Resume", 180, 320);
    }

    private void drawGameOverOverlay(Graphics2D g) {
        drawOverlayBox(g, "GAME OVER");
        g.setColor(Color.RED);
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.drawString("Your torch burned out in the dark!", 280, 250);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.drawString("Press ENTER to Restart", 340, 310);
    }

    private void drawOverlayBox(Graphics2D g, String title) {
        g.setColor(new Color(0, 0, 0, 215));
        g.fillRect(140, 90, Constants.WINDOW_WIDTH - 280, 320);
        g.setColor(new Color(255, 215, 0));
        g.drawRect(140, 90, Constants.WINDOW_WIDTH - 280, 320);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.drawString(title, 170, 135);
    }
}