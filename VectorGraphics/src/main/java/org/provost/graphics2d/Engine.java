package org.provost.graphics2d;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.provost.graphics2d.graphics.BasicVector;
import org.provost.graphics2d.graphics.MainWin;
import org.provost.graphics2d.graphics.objects.Coordinates;
import org.provost.graphics2d.graphics.objects.Triangle;
import org.provost.profile.FPSProfiler;

public class Engine extends Thread {

	private static final Logger log = Logger.getLogger(Engine.class);
	private static final Engine instance = new Engine();

	// Engine-internal properties
	private boolean run = true;
	private boolean pause = false;
	private long lastRun = System.currentTimeMillis();

	private Configuration config = null;
	private List<BasicVector> objects = new ArrayList<BasicVector>();

	private Engine() {
		super();
		this.config = new Configuration();
		log.info("Engine created");
	}

	public static Engine getInstance() {
		return instance;
	}

	@Override
	public void run() {
		try {
			log.debug("Begin");
			MainWin mainWin = MainWin.getInstance();
			mainWin.show();
			FPSProfiler fpsProfiler = new FPSProfiler();
			createObjects();
			while(run) {
				fpsProfiler.startFrame(new Date().getTime());

				updateGameState();

				updateWindow();

				syncFramerate();

				fpsProfiler.endFrame(new Date().getTime());
				
				logFps(fpsProfiler);
				
			}
		} finally {
			log.debug("Stopped running");
			/*
			 * If Engine is stopped (error occurs) before MainWin fully initializes,
			 * MainWin will get displayed even without Engine running.
			 * This ensures that MainWin is really disposed when Engine stops running.
			 */
			disposeWindow();
		}
	}

	private void logFps(FPSProfiler fpsProfiler) {
		if(fpsProfiler.getNumFrames() % 50L == 0) {
			log.debug("FPS: " + fpsProfiler.calculateOverallFps());
		}
	}

	private void createObjects() {
		Triangle ship = new Triangle(new Coordinates(50, (MainWin.getInstance().getHeight() / 2) - 5)
			, new Coordinates(50, (MainWin.getInstance().getHeight() / 2) + 5)
			, new Coordinates(60, MainWin.getInstance().getHeight() / 2)
			, Color.lightGray);
		objects.add(ship);
		/* benchmarking
		// 10000 non-antialiased triangles
		// 200 antialiased triangles: 30 fps
		java.util.Random rnd = new java.util.Random(new Date().getTime());
		for(int i = 0; i < 8000; i++) {
			int x = rnd.nextInt(631);
			if(x < 10) {
				x += 10;
			}
			int y = rnd.nextInt(471);
			if(y < 10) {
				y += 10;
			}
			ship = new Triangle(new Coordinates(x, y - 5)
				, new Coordinates(x, y + 5)
				, new Coordinates(x + 10, y)
				, Color.lightGray);
			objects.add(ship);
		}
		*/
	}

	private void syncFramerate() {
		// wait for next cycle
		lastRun += Constants.FRAME_DELAY;
		// calculate time for next run
		long nextRunDiff = lastRun - System.currentTimeMillis();
		synchronized(this) {
			try {
				if(pause) {
					// wait until un-paused
					this.wait();
				} else {
					// wait for next frame
					log.trace("Next run in: " + nextRunDiff);
					this.wait(Math.max(1, nextRunDiff));
				}
			} catch (InterruptedException e) {
				log.error("Interrupted", e);
			}
		}
	}

	private void updateWindow() {
		// blank canvas
		Graphics2D g = null;
		try {
			synchronized(MainWin.getInstance().getCanvas().getBufferStrategy()) {
				g = (Graphics2D) MainWin.getInstance().getCanvas().getBufferStrategy().getDrawGraphics();
			}

			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(Color.black);
			g.fillRect(0, 0, MainWin.getInstance().getWidth(), MainWin.getInstance().getHeight());

			// TODO add custom graphics to display
			for(int i = 0; i < objects.size(); i++) {
				BasicVector object = objects.get(i);
				object.paint(g);
			}
		} finally {
			if(g != null) {
				g.dispose();
			}
		}
		// drawing completed, display
		MainWin.getInstance().getCanvas().getBufferStrategy().show();
		
		// v-sync
		Toolkit.getDefaultToolkit().sync();
	}

	private void updateGameState() {
		lastRun = System.currentTimeMillis();
		// TODO any internal game logic
		
		for(int i = 0; i < objects.size(); i++) {
			BasicVector object = objects.get(i);
			// rotation of a static object
			object.rotate(15);
		/*
			// TODO collision detection...
			// straight movement
			object.move();
		*/
		}
	}

	public void end() {
		log.debug("Ending");
		setRun(false);
		synchronized(this) {
			this.notifyAll();
		}
		disposeWindow();
	}

	private void disposeWindow() {
		if(MainWin.getInstance() != null) {
			MainWin.getInstance().end();
		}
	}

	@Override
	public void finalize() {
		end();
	}

	public boolean isRun() {
		return run;
	}

	public void setRun(boolean run) {
		this.run = run;
	}

	public boolean isPause() {
		return pause;
	}

	public void setPause(boolean pause) {
		this.pause = pause;
		if(pause == false) {
			// un-pause
			synchronized(this) {
				try {
					this.notifyAll();
				} catch(Exception x) {
					log.error("Invalid state", x);
				}
			}
		}
	}

	public Configuration getConfig() {
		return config;
	}

}
