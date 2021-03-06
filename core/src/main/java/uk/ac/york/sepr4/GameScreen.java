package uk.ac.york.sepr4;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.TiledMapRenderer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import lombok.Getter;
import uk.ac.york.sepr4.hud.HUD;
import uk.ac.york.sepr4.hud.HealthBar;
import uk.ac.york.sepr4.object.PirateMap;
import uk.ac.york.sepr4.object.building.BuildingManager;
import uk.ac.york.sepr4.object.building.Department;
import uk.ac.york.sepr4.object.entity.EntityManager;
import uk.ac.york.sepr4.object.entity.LivingEntity;
import uk.ac.york.sepr4.object.entity.NPCBoat;
import uk.ac.york.sepr4.object.entity.Player;
import uk.ac.york.sepr4.object.item.ItemManager;
import uk.ac.york.sepr4.object.projectile.Projectile;
import uk.ac.york.sepr4.object.quest.QuestManager;
import uk.ac.york.sepr4.utils.AIUtil;

import java.awt.*;
import java.util.Random;

/**
 * GameScreen is main game class. Holds data related to current player including the
 * {@link BuildingManager}, {@link ItemManager},
 * {@link QuestManager} and {@link EntityManager}
 * <p>
 * Responds to keyboard and mouse input by the player. InputMultiplexer used to combine input processing in both
 * this class (mouse clicks) and {@link Player} class (key press).
 */
public class GameScreen implements Screen, InputProcessor {

    private PirateGame pirateGame;
    private Stage stage;
    private Stage hudStage;
    private SpriteBatch batch;

    @Getter
    private OrthographicCamera orthographicCamera;

    @Getter
    PirateMap pirateMap;
    TiledMapRenderer tiledMapRenderer;

    private ItemManager itemManager;
    @Getter
    private EntityManager entityManager;
    //Use this entitymanager for finding the player
    @Getter
    private QuestManager questManager;
    @Getter
    private BuildingManager buildingManager;

    private InputMultiplexer inputMultiplexer;

    private HUD hud;

    private static GameScreen gameScreen;


    private ShapeRenderer shapeRenderer;

    public static boolean DEBUG = true;

    private TextButton upgradeShipSpeedButton;

    //Not sure these belongs here tbh...
    private int minigameCost = 50, minigameReward = 100;

    public int shipSpeedUpgradeCost = 100;
    public int shipHealthUpgradeCost = 100;
    public int shipDamageUpgradeCost = 100;

    // Tracking the incrementing of the player XP
    private float playerXpIncrementer;
    private float incrementSpeed;

    public static GameScreen getInstance() {
        return gameScreen;
    }

    /**
     * GameScreen Constructor
     * <p>
     * Sets base game parameters, sets up camera, map, view port and stage(s).
     * Initializes Item, Entity, Building and Quest managers and InputMultiplexer.
     * <p>
     * Adds the player as an actor to the stage.
     *
     * @param pirateGame
     */
    public GameScreen(PirateGame pirateGame) {
        this.pirateGame = pirateGame;
        gameScreen = this;

        playerXpIncrementer = 0f;
        incrementSpeed = 1f;

        // Debug options (extra logging, collision shape renderer (viewing tile object map))
        if(DEBUG) {
            Gdx.app.setLogLevel(Application.LOG_DEBUG);
            shapeRenderer = new ShapeRenderer();
        }

        // temporary until we have asset manager in
        Skin skin = new Skin(Gdx.files.internal("default_skin/uiskin.json"));

        // Local widths and heights.
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        // Set up camera.
        orthographicCamera = new OrthographicCamera();
        orthographicCamera.setToOrtho(false, w, h);
        orthographicCamera.update();

        // Set up stages (for entities and HUD).
        StretchViewport stretchViewport = new StretchViewport(w, h, orthographicCamera);
        batch = new SpriteBatch();
        stage = new Stage(stretchViewport, batch);
        hudStage = new Stage(new FitViewport(w, h, new OrthographicCamera()));

        // Locate and set up tile map.
        pirateMap = new PirateMap(new TmxMapLoader().load("PirateMap/PirateMap.tmx"));
        tiledMapRenderer = new OrthogonalTiledMapRenderer(pirateMap.getTiledMap(), 1 / 2f);

        // Initialize game managers
        this.itemManager = new ItemManager();
        this.entityManager = new EntityManager(this);
        this.questManager = new QuestManager(entityManager);
        this.buildingManager = new BuildingManager(this);

        // Create HUD (display for xp, gold, etc..)
        this.hud = new HUD(this);
        hudStage.addActor(this.hud.getTable());

        // Set input processor and focus
        inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(this);
        inputMultiplexer.addProcessor(entityManager.getOrCreatePlayer());
        Gdx.input.setInputProcessor(inputMultiplexer);
        Gdx.input.setInputProcessor(stage);



        //create and spawn player
        startGame();
    }

    private void startGame() {
        stage.addActor(entityManager.getOrCreatePlayer());
    }

    /**
     * Called when screen becomes active (is switched to).
     */
    @Override
    public void show() {
        Gdx.input.setInputProcessor(inputMultiplexer);
    }

    /**
     * Method responsible for rendering the GameScreen on each frame. This clears the screen, updates the map and
     * visible entities, then calls the stage act. This causes actors (entities) on the stage to move (act).
     *
     * @param delta Time between last and current frame.
     */
    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        //if player dead, go to main menu
        Player player = entityManager.getOrCreatePlayer();
        player.setLocation(entityManager.getPlayerLocation());
        if (player.isDead()) {
            Gdx.app.debug("GameScreen", "Player Died!");
            pirateGame.switchScreen(ScreenType.MENU);
            pirateGame.gameOver();
            return;
        }

        upgradeStats();
        menuUpdate();

        if(!player.isDying()) {

            //spawns/despawns entities, handles animations and projectiles
            entityManager.handleStageEntities(stage, delta);
        } else {
            //when the player is dying - only process animations
            getEntityManager().getAnimationManager().handleEffects(stage,delta);
        }
            if (pirateMap.isObjectsEnabled()) {
                buildingManager.spawnCollegeEnemies(delta);
                buildingManager.checkBossSpawn();
                buildingManager.checkMonsterSpawn();
            }

            handleHealthBars();

            this.hud.update();

            checkCollisions();

            // Update camera and focus on player.
            orthographicCamera.position.set(player.getX() + player.getWidth() / 2f, player.getY() + player.getHeight() / 2f, 0);
            orthographicCamera.update();
            batch.setProjectionMatrix(orthographicCamera.combined);
            tiledMapRenderer.setView(orthographicCamera);
            tiledMapRenderer.render();

            //debug
            if (DEBUG) {
                shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
                shapeRenderer.setColor(Color.RED);

                for (Polygon polygonMapObject : getPirateMap().getCollisionObjects()) {
                    shapeRenderer.polygon(polygonMapObject.getTransformedVertices());
                }
                shapeRenderer.end();
            }


        stage.act(delta);
        stage.draw();
        hudStage.act();
        hudStage.draw();
        this.incrementPlayerXP(delta, player);
    }

    /**
     * Handles the incrementing of the player's XP
     */
    private void incrementPlayerXP(float delta, Player player){
        playerXpIncrementer += delta * incrementSpeed;
        //Gdx.app.debug("GameScreen", String.format("%.02f", playerXpIncrementer));
       // Fps 50, 1/50
        if (playerXpIncrementer > 1){
            player.addXP(1);

            playerXpIncrementer = 0;
        }

    }
    /**
     * Handles in game pause menu changes
     */
    private void menuUpdate(){
        if(Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)){
            this.pirateGame.getMenuScreen().setIsGameStarted(true);
            this.pirateGame.switchScreen(ScreenType.MENU);
            this.pirateGame.getMenuScreen().setIsGameStarted(false);
        }

    }

    /**
     * Handles HealthBar elements for damaged actors.
     */
    private void handleHealthBars() {
        Player player = entityManager.getOrCreatePlayer();
        if (player.getHealth() < player.getMaxHealth()) {
            if (!stage.getActors().contains(player.getHealthBar(), true)) {
                stage.addActor(player.getHealthBar());
            }
        }

        for (uk.ac.york.sepr4.object.entity.NPCBoat NPCBoat : entityManager.getNpcList()) {
            if (NPCBoat.getHealth() < NPCBoat.getMaxHealth()) {
                if (!stage.getActors().contains(NPCBoat.getHealthBar(), true)) {
                    stage.addActor(NPCBoat.getHealthBar());
                }
            }
        }
        Array<Actor> toRemove = new Array<Actor>();
        for (Actor actors : stage.getActors()) {
            if (actors instanceof HealthBar) {
                HealthBar healthBar = (HealthBar) actors;
                LivingEntity livingEntity = healthBar.getLivingEntity();
                if (livingEntity.getHealth() == livingEntity.getMaxHealth() || livingEntity.isDead() || livingEntity.isDying()) {
                    toRemove.add(actors);
                }
            }
        }
        stage.getActors().removeAll(toRemove, true);

    }

    /**
     * Checks whether actors have overlapped. In the instance where projectile and entity overlap, deal damage.
     */
    private void checkCollisions() {
        checkProjectileCollisions();
        checkLivingEntityCollisions();
    }

    public void checkLivingEntityCollisions() {
        //player/map collision check
        //TODO: Improve to make player a polygon
        for(LivingEntity lE : getEntityManager().getLivingEntities()) {
            //Between entity and map
            if(getPirateMap().isColliding(lE.getRectBounds())) {
                if (lE.getCollidedWithIsland() == 0) {
                    lE.collide(false, 0f);
                }
            }
            if(lE.getCollidedWithIsland() >= 1) {
                lE.setCollidedWithIsland(lE.getCollidedWithIsland() - 1);
            }

            //between living entities themselves
            //TODO: still a bit buggy
            for(LivingEntity lE2 : getEntityManager().getLivingEntities()) {
                if(!lE.equals(lE2)) {
                    if(lE.getRectBounds().overlaps(lE2.getRectBounds())) {
                        if(lE.getColliedWithBoat() == 0) {
                            lE.collide(true, AIUtil.normalizeAngle((float)(lE.getAngleTowardsEntity(lE2) - Math.PI)));
                        }
                        //Gdx.app.log("gs", ""+lE.getColliedWithBoat());
                    }
                }
                if(lE.getColliedWithBoat() >= 1) {
                    lE.setColliedWithBoat(lE.getColliedWithBoat() - 1);
                }
            }
        }
    }

    private void checkProjectileCollisions() {
        Player player = entityManager.getOrCreatePlayer();

        for (LivingEntity livingEntity : entityManager.getLivingEntities()) {
            for (Projectile projectile : entityManager.getProjectileManager().getProjectileList()) {
                if (projectile.getShooter() != livingEntity && projectile.getRectBounds().overlaps(livingEntity.getRectBounds())) {
                    //if bullet overlaps player and shooter not player
                    if (!(livingEntity.isDying() || livingEntity.isDead())) {
                        if (!livingEntity.damage(projectile.getDamage())) {
                            //is dead
                            if (livingEntity instanceof NPCBoat) {
                                Gdx.app.debug("GameScreen", "NPCBoat died.");
                                NPCBoat npcBoat = (NPCBoat) livingEntity;
                                player.issueReward(itemManager.generateReward());
                                //if dead NPC is a boss then player can capture its respective college
                                if (npcBoat.isBoss() && npcBoat.getAllied().isPresent()) {
                                    // find if college is part of quest
                                    // First null check is to avoid null pointer errors when checking the current quest
                                    // The second predicate ensures the quest is a kill quest since this is section is checking whether a kill also completed a quest
                                    if ((this.questManager.getCurrentQuest() != null) && (this.questManager.getCurrentQuest().getIsKillQuest())) {
                                        //Compares the current target of the kill quest vs. the name of the allied college of the npc
                                        //We use string matching here because quests do not store targets as College instances but as Strings for their name
                                        if (this.questManager.getCurrentQuest().getTargetEntityName().equals(npcBoat.getAllied().get().getName())){
                                            this.questManager.finishCurrentQuest();
                                            player.issueReward(itemManager.generateReward());
                                        }
                                    }
                                    player.capture(npcBoat.getAllied().get());
                                }
                            } else {
                                Gdx.app.debug("GameScreen", "Player died.");
                            }
                        }
                        Gdx.app.debug("GameScreen", "LivingEntity damaged by projectile.");
                        //kill projectile
                        projectile.setActive(false);
                    }
                }
            }
        }
    }

    //NEW METHOD
    /**
     * Gambles the players money with the chance to double their money.
     * (Follows the requirement of needing to be at a department allied with a captured college: ReqID 2.14)
     */
    public void doMinigame(){
       Player player = entityManager.getOrCreatePlayer();
       Random random = new Random();
       if (player.getBalance() < minigameCost) {
            System.out.println("Not enough gold to play!");
        }
        else if (entityManager.getPlayerDepartmentLocation().isPresent()){
            Department currentDepartment = entityManager.getPlayerDepartmentLocation().get();
            //Checks whether the player is at a department which is allied with a college which they have captured (ReqID 2.13)
            if (player.getCaptured().contains(currentDepartment.getAllied())) {
                player.setBalance(player.getBalance() - minigameCost);
                Integer result = Math.round(random.nextFloat() * minigameReward);
                player.setBalance(player.getBalance() + result);
            }
            else {
                //TODO: Show this on the HUD
                System.out.println("You have not captured the allied college of this department yet!");
            }
       }
       else {
           //TODO: Show this on the HUD
           System.out.println("You are not in the right location to play!");
       }
       player.setInMinigame(false);
    }

    /**
     * NEW - Used to upgrade stats of the ship using the gold earned by defeating monsters/ships
     * (Follows the requirement of allowing the player to upgrade/repair ship)
     */
    public void upgradeStats() {
        Player player = this.entityManager.getOrCreatePlayer();

        //Number keys will be used to upgrade the items
        //Our players balance needs to be higher than the upgrade cost for the transaction to go through
        if(Gdx.input.isKeyJustPressed(Input.Keys.NUM_1) && player.getBalance() >= shipSpeedUpgradeCost){
            //increase the ships maximum speed by a constant multiplier
            player.setMaxSpeed((float) (player.getMaxSpeed() * 1.5));
            player.setTurningSpeed(player.getTurningSpeed() + 1);

            //once the user has upgraded their ship speed, reduce their gold balance
            player.setBalance(player.getBalance() - shipSpeedUpgradeCost);

            //once player have upgraded their ship, increase the price for an extra upgrade
            shipSpeedUpgradeCost = shipSpeedUpgradeCost * 2;
            hud.upgradeShipSpeedButton.setText("Upgrade ship speed - Required: " + shipSpeedUpgradeCost + "gold [press 1]");
        }

        if(Gdx.input.isKeyJustPressed(Input.Keys.NUM_2) && player.getBalance() >= shipHealthUpgradeCost){
            //increase the ships maximum speed by a constant multiplier
            player.setMaxHealth(player.getMaxHealth() * 1.5);
            player.setHealth(player.getMaxHealth());

            //once the user has upgraded their ship health, reduce their gold balance
            player.setBalance(player.getBalance() - shipHealthUpgradeCost);

            //once player have upgraded their ship, increase the price for an extra upgrade
            shipHealthUpgradeCost = shipHealthUpgradeCost * 2;
            hud.upgradeshipHealthButton.setText("Upgrade ship health - Required: " + shipHealthUpgradeCost + "gold [press 2]");
        }

        if(Gdx.input.isKeyJustPressed(Input.Keys.NUM_3) && player.getBalance() >= shipDamageUpgradeCost){
            //increase the ships maximum damage done by a constant multiplier
            player.setDamage(player.getDamage() * 1.5);

            //once the user has upgraded their damage cannon damage, reduce their gold balance
            player.setBalance(player.getBalance() - shipDamageUpgradeCost);

            //once player have upgraded their ship, increase the price for an extra upgrade
            shipDamageUpgradeCost = shipDamageUpgradeCost * 2;
            hud.upgradeShipDamageButton.setText("Upgrade cannon damage - Required: " + shipDamageUpgradeCost + "gold [press 3]");
        }
    }

    @Override
    public void resize(int width, int height) {
        orthographicCamera.setToOrtho(false, (float) width, (float) height);
        orthographicCamera.update();

        stage.getViewport().update(width, height);
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {Gdx.input.setInputProcessor(stage);}

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button == Input.Buttons.LEFT) {
            Player player = entityManager.getOrCreatePlayer();
            Vector3 clickLoc = orthographicCamera.unproject(new Vector3(screenX, screenY, 0));
            float fireAngle = (float) (-Math.atan2(player.getCentre().x - clickLoc.x, player.getCentre().y - clickLoc.y));
            Gdx.app.debug("GameScreen", "Firing: Click at (rad) " + fireAngle);
            if (!player.fire(fireAngle)) {
                Gdx.app.debug("GameScreen", "Firing: Error! (cooldown?)");
            }
            return true;
        }
        return false;
    }


    // Stub methods for InputProcessor (unused) - must return false (otherwise key detection for that method will not run in Player.java
    @Override
    public boolean keyDown(int keycode) {
        return false;
    }

    @Override
    /**
     * Currently only used to detect when the player has pressed tab to play the minigame.
     * Uses keyUp not keyDown so that holding down the key has no effect.
     * @return False so that the key detection does not terminate here
     */
    public boolean keyUp(int keycode) {
        Player player = entityManager.getOrCreatePlayer();
        if((keycode == Input.Keys.TAB) && (player.isInMinigame() == false)){
            player.setInMinigame(true);
            doMinigame();
        }
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(int amount) {
        return false;
    }

}
