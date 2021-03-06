package uk.ac.york.sepr4;

import com.badlogic.gdx.Game;
import lombok.Getter;

public class PirateGame extends Game {
	@Getter
	private MenuScreen menuScreen;
	@Getter
	private GameScreen gameScreen;


	public static PirateGame PIRATEGAME;
	
	@Override
	public void create () {
		PIRATEGAME = this;
	    //switchScreen(ScreenType.MENU);
		//FOR DEVELOPMENT
		switchScreen(ScreenType.MENU);
	}

	public void gameOver() {
		gameScreen = null;
	}

	public void switchScreen(ScreenType screenType){
		switch (screenType) {
			case MENU:
				if(menuScreen == null) menuScreen = new MenuScreen(this);
				this.setScreen(menuScreen);
				break;
            case GAME:
                if(gameScreen == null) gameScreen = new GameScreen(this);
                this.setScreen(gameScreen);
                break;
		}
	}
}
