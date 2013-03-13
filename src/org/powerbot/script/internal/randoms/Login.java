package org.powerbot.script.internal.randoms;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import org.powerbot.bot.Bot;
import org.powerbot.game.api.methods.Game;
import org.powerbot.game.api.methods.Widgets;
import org.powerbot.game.api.methods.input.Keyboard;
import org.powerbot.game.api.methods.input.Mouse;
import org.powerbot.game.api.methods.widget.Lobby;
import org.powerbot.game.api.util.Random;
import org.powerbot.game.api.util.Timer;
import org.powerbot.game.api.wrappers.widget.WidgetChild;
import org.powerbot.game.bot.Context;
import org.powerbot.script.TaskScript;
import org.powerbot.script.event.PaintListener;
import org.powerbot.script.task.BlockingTask;

/**
 * @author Timer
 */
@RandomManifest(name = "Login")
public class Login extends TaskScript implements PaintListener {
	private static final int WIDGET = 596;
	private static final int WIDGET_LOGIN_ERROR = 13;
	private static final int WIDGET_LOGIN_TRY_AGAIN = 65;
	private static final int WIDGET_LOGIN_USERNAME_TEXT = 70;
	private static final int WIDGET_LOGIN_PASSWORD_TEXT = 76;
	private static final int WIDGET_LOBBY = 906;
	private static final int WIDGET_LOBBY_ERROR = 249;
	private static final int WIDGET_LOBBY_TRY_AGAIN = 259;
	private final Bot bot;
	private volatile Timer re_load_timer = null;

	public Login() {
		this.bot = Bot.instance();
		submit(new LoginTask());
		submit(new LobbyTask());
	}

	private boolean clickLoginInterface(final WidgetChild i) {
		if (!i.validate()) {
			return false;
		}
		final Rectangle pos = i.getBoundingRectangle();
		if (pos.x == -1 || pos.y == -1 || pos.width == -1 || pos.height == -1) {
			return false;
		}
		final int dy = (int) (pos.getHeight() - 4) / 2;
		final int maxRandomX = (int) (pos.getMaxX() - pos.getCenterX());
		final int midx = (int) pos.getCenterX();
		final int midy = (int) (pos.getMinY() + pos.getHeight() / 2);
		if (i.getIndex() == WIDGET_LOGIN_PASSWORD_TEXT) {
			return Mouse.click(getPasswordX(i), midy + Random.nextInt(-dy, dy), true);
		}
		return Mouse.click(midx + Random.nextInt(1, maxRandomX), midy + Random.nextInt(-dy, dy), true);
	}

	private int getPasswordX(final WidgetChild a) {
		int x = 0;
		final Rectangle pos = a.getBoundingRectangle();
		final int dx = (int) (pos.getWidth() - 4) / 2;
		final int midx = (int) (pos.getMinX() + pos.getWidth() / 2);
		if (pos.x == -1 || pos.y == -1 || pos.width == -1 || pos.height == -1) {
			return 0;
		}
		for (int i = 0; i < Widgets.get(WIDGET, WIDGET_LOGIN_PASSWORD_TEXT).getText().length(); i++) {
			x += 11;
		}
		if (x > 44) {
			return (int) (pos.getMinX() + x + 15);
		} else {
			return midx + Random.nextInt(-dx, dx);
		}
	}

	private boolean isUsernameCorrect() {
		final String userName = bot.getAccount().toString();
		return Widgets.get(WIDGET, WIDGET_LOGIN_USERNAME_TEXT).getText().toLowerCase().equalsIgnoreCase(userName);
	}

	private boolean isPasswordValid() {
		String passWord = bot.getAccount().getPassword();
		return Widgets.get(WIDGET, WIDGET_LOGIN_PASSWORD_TEXT).getText().length() == (passWord == null ? 0 : passWord.length());
	}

	private void attemptLogin() {
		Keyboard.sendKey('\n', Random.nextInt(100, 200));
	}

	private void erase(final int count) {
		for (int i = 0; i <= count + Random.nextInt(1, 5); i++) {
			Keyboard.sendKey('\b', Random.nextInt(50, 150));
			if (Random.nextInt(0, 2) == 1) {
				sleep(Random.nextInt(25, 100));
			}
		}
	}

	@Override
	public void onRepaint(final Graphics render) {
		if (re_load_timer != null) {
			render.setColor(Color.white);
			render.drawString("Reloading game in: " + re_load_timer.toRemainingString(), 8, 30);
		}
	}

	private enum LoginEvent {
		TOKEN_FAILURE(WIDGET_LOGIN_ERROR, "game session", 1000 * 5 * 60, new FutureTask<Boolean>(new Runnable() {
			@Override
			public void run() {
				Context.resolve().refresh();
			}
		}, true)),
		INVALID_PASSWORD(WIDGET_LOGIN_ERROR, "Invalid username or password", -1);
		private final String message;
		private final int child, wait;
		private final FutureTask<Boolean> task;

		LoginEvent(final int child, final String message, final int wait, final FutureTask<Boolean> task) {
			this.child = child;
			this.message = message;
			this.wait = wait;
			this.task = task;
		}

		LoginEvent(final int child, final String message, final int wait) {
			this(child, message, wait, null);
		}
	}

	private enum LobbyEvent {
		LOGGED_IN(WIDGET_LOBBY_ERROR, "last session", Random.nextInt(1000, 4000));
		private final String message;
		private final int child, wait;
		private final FutureTask<Boolean> task;

		LobbyEvent(final int child, final String message, final int wait, final FutureTask<Boolean> task) {
			this.child = child;
			this.message = message;
			this.wait = wait;
			this.task = task;
		}

		LobbyEvent(final int child, final String message, final int wait) {
			this(child, message, wait, null);
		}
	}

	private final class LoginTask extends BlockingTask {
		@Override
		public boolean isValid() {
			final int state = Game.getClientState();
			return (state == Game.INDEX_LOGIN_SCREEN || state == Game.INDEX_LOGGING_IN) && bot.getAccount() != null;
		}

		@Override
		public Boolean call() {
			for (final LoginEvent loginEvent : LoginEvent.values()) {
				final WidgetChild widgetChild = Widgets.get(WIDGET, loginEvent.child);
				if (widgetChild != null && widgetChild.validate()) {
					final String text = widgetChild.getText().toLowerCase().trim();
					Widgets.get(WIDGET, WIDGET_LOGIN_TRY_AGAIN).click(true);

					if (text.contains(loginEvent.message.toLowerCase())) {
						log.info("Handling login event: " + loginEvent.name());
						boolean set_timer = loginEvent.equals(LoginEvent.TOKEN_FAILURE);

						if (set_timer && loginEvent.wait > 0) {
							re_load_timer = new Timer(loginEvent.wait);
						}
						if (loginEvent.wait > 0) {
							sleep(loginEvent.wait);
						} else if (loginEvent.wait == -1) {
							getScriptController().stop();
							return false;
						}

						re_load_timer = null;
						if (loginEvent.task != null) {
							try {
								loginEvent.task.get();
							} catch (final InterruptedException | ExecutionException ignored) {
							}
						}
						return false;
					}
				}
			}

			if (isUsernameCorrect() && isPasswordValid()) {
				attemptLogin();
				sleep(Random.nextInt(1200, 2000));
			} else if (!isUsernameCorrect()) {
				final String username = bot.getAccount().toString();
				final WidgetChild usernameTextBox = Widgets.get(WIDGET, WIDGET_LOGIN_USERNAME_TEXT);
				if (!clickLoginInterface(usernameTextBox)) {
					return false;
				}
				sleep(Random.nextInt(500, 700));
				final int textLength = usernameTextBox.getText().length();
				if (textLength > 0) {
					erase(textLength);
					return false;
				}
				Keyboard.sendText(username, false);
				sleep(Random.nextInt(500, 700));
			} else if (!isPasswordValid()) {
				final String password = bot.getAccount().getPassword();
				final WidgetChild passwordTextBox = Widgets.get(WIDGET, WIDGET_LOGIN_PASSWORD_TEXT);
				if (!clickLoginInterface(passwordTextBox)) {
					return false;
				}
				sleep(Random.nextInt(500, 700));
				final int textLength = passwordTextBox.getText().length();
				if (textLength > 0) {
					erase(textLength);
					return false;
				}
				Keyboard.sendText(password, false);
				sleep(Random.nextInt(500, 700));
			}
			return true;
		}
	}

	private final class LobbyTask extends BlockingTask {

		@Override
		public boolean isValid() {
			final int state = Game.getClientState();
			return state == Game.INDEX_LOBBY_SCREEN && bot.getAccount() != null;
		}

		@Override
		public Boolean call() {
			for (final LobbyEvent lobbyEvent : LobbyEvent.values()) {
				final WidgetChild widgetChild = Widgets.get(WIDGET_LOBBY, lobbyEvent.child);
				if (widgetChild != null && widgetChild.validate()) {
					final String text = widgetChild.getText().toLowerCase().trim();

					if (text.contains(lobbyEvent.message.toLowerCase())) {
						log.info("Handling lobby event: " + lobbyEvent.name());
						Widgets.get(WIDGET_LOBBY, WIDGET_LOBBY_TRY_AGAIN).click(true);

						if (lobbyEvent.wait > 0) {
							sleep(lobbyEvent.wait);
						} else if (lobbyEvent.wait == -1) {
							bot.getScriptController().stop();
							return false;
						}

						if (lobbyEvent.task != null) {
							try {
								lobbyEvent.task.get();
							} catch (final InterruptedException | ExecutionException ignored) {
							}
						}
						return false;
					}
				}
			}

			final int world = Context.get().world;
			if (world > 0) {
				final Lobby.World world_wrapper;
				if ((world_wrapper = Lobby.getWorld(world)) != null) {
					Lobby.enterGame(world_wrapper);
					return true;
				}
			}
			Lobby.enterGame();
			return true;
		}
	}
}
