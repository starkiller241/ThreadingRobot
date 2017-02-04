
package org.usfirst.frc.team4950.robot;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import org.usfirst.frc.team4950.robot.subsystems.ExampleSubsystem;

import edu.wpi.first.wpilibj.IterativeRobot;
import edu.wpi.first.wpilibj.buttons.Button;
import edu.wpi.first.wpilibj.buttons.InternalButton;
import edu.wpi.first.wpilibj.command.Command;
import edu.wpi.first.wpilibj.command.Scheduler;
import edu.wpi.first.wpilibj.command.Subsystem;
import edu.wpi.first.wpilibj.livewindow.LiveWindow;

public class ThreadingRobot extends IterativeRobot {
	private PrintStream out;
	private int server_port;
	private UpdaterThread updater;
	private FlusherThread flush;
	private SensorThread sense;
	public Database database;
	Map<String, Supplier<Command>> commandsMap;
	Map<String, Subsystem> subsystems;
	public ArrayBlockingQueue<String> tempData;
	private int delay = 10;

	@Override
	public void robotInit() {
		database = new Database(this);
		sense = new SensorThread(this, delay);

		// code for creating all networking threads
		ServerSocket server;

		try {
			server = new ServerSocket(server_port);
			Socket socket = server.accept();
			OutputStream out_stream = socket.getOutputStream();
			out = new PrintStream(out_stream);
		} catch (IOException e) {
			e.printStackTrace();
		}

		subsystems = new HashMap<>();
		commandsMap = new HashMap<>();
		updater = new UpdaterThread(this, commandsMap);
		flush = new FlusherThread(this, out);
		addCommandListeners();
	}

	public int getDelay() {
		return delay;
	}

	public void setDelay(int delay) {
		this.delay = delay;
	}

	// Method to override
	public Map<String, DoubleSupplier> deviceCalls() {
		Map<String, DoubleSupplier> returner = new HashMap<>();

		return returner;
	}

	// Method to override
	public ArrayList<String> setDeviceIDs() {
		return new ArrayList<String>();
	}

	// Method to override
	public ArrayList<String> setButtonRefs() {
		return new ArrayList<String>();
	}

	// Method to override
	public void resetEncoders() {

	}

	// Method to override
	public void resetGyro() {

	}

	public void startThreads() {
		sense.start();
		updater.start();
		flush.start();
	}

	public void addCommandListeners() {
		for (String key : subsystems.keySet()) {
			commandsMap.put(key, () -> subsystems.get(key).getCurrentCommand());
		}
	}

	public void addSystems() {
		// subsystems.put("ExampleSubsystem", new ExampleSubsystem());
	}

	@Override
	public void disabledInit() {

	}

	@Override
	public void disabledPeriodic() {
		Scheduler.getInstance().run();
	}

	@Override
	public void autonomousInit() {

	}

	@Override
	public void autonomousPeriodic() {
		Scheduler.getInstance().run();
	}

	@Override
	public void teleopInit() {

	}

	@Override
	public void teleopPeriodic() {
		Scheduler.getInstance().run();
	}

	@Override
	public void testPeriodic() {
		LiveWindow.run();
	}

	public void setPort(int server) {
		server_port = server;
	}

	public int getPort() {
		return server_port;
	}
}

class SensorThread extends Thread {
	private ThreadingRobot robot;
	private final int TIME;
	private volatile boolean alive = true;
	private Map<String, Double> temp;
	private Map<String, DoubleSupplier> callMap;

	public SensorThread(ThreadingRobot bot, int seconds) {
		robot = bot;
		TIME = seconds;
		temp = new HashMap<>();
		callMap = robot.deviceCalls();
		resetDevices();
	}

	public SensorThread(ThreadingRobot bot) {
		robot = bot;
		TIME = 10;
		temp = new HashMap<>();
		callMap = robot.deviceCalls();
		callMap = Collections.unmodifiableMap(callMap);
		resetDevices();
	}

	@Override
	public void run() {
		while (alive) {
			updateDevices();
			try {
				Thread.sleep(TIME);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public void kill() {
		alive = false;
	}

	public boolean isDead() {
		return !alive;
	}

	public void resetDevices() {
		robot.resetEncoders();
		robot.resetGyro();
	}

	public void updateDevices() {
		for (String key : callMap.keySet()) {
			temp.put(key, callMap.get(key).getAsDouble());//
		}

		for (String key : temp.keySet()) {
			robot.database.setValue(key, temp.get(key));
		}
	}
}

class UpdaterThread extends Thread {
	Map<String, Supplier<Command>> commandMap;
	ThreadingRobot robot;
	boolean alive = true;

	public UpdaterThread(ThreadingRobot bot, Map<String, Supplier<Command>> systems) {
		robot = bot;
		commandMap = systems;
		super.setDaemon(true);
	}

	@Override
	public void run() {
		while (alive) {
			try {
				String str = "";
				for (String key : robot.database.getDeviceIDs()) {
					str += (robot.database.getValue(key) + " ");
				}

				for (String s : commandMap.keySet()) {
					boolean b = commandMap.get(s).get() != null;
					str += (b + " ");
				}
				robot.tempData.add(str);
				Thread.sleep(robot.getDelay());
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public void end() {
		alive = false;
	}
}

class FlusherThread extends Thread {
	boolean alive;
	ThreadingRobot robot;
	PrintStream out;
	int delay;

	public FlusherThread(ThreadingRobot bot, PrintStream out) {
		robot = bot;
		this.out = out;
		alive = true;
		super.setDaemon(false);

		if (1000 / robot.getDelay() >= 10) {
			delay = 1000;
		} else {
			delay = robot.getDelay() * 10;
		}
	}

	public void end() {
		alive = false;
	}

	@Override
	public void run() {
		while (alive) {
			ArrayList<String> arr = new ArrayList<>();
			robot.tempData.drainTo(arr);

			for (int i = 0; i < arr.size(); i++) {
				out.println(arr.get(i));
			}

			try {
				Thread.sleep(delay);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}

class Database {
	private ThreadingRobot robot;
	private Map<String, ThreadSafeHolder> deviceMap;
	private Map<String, ThreadSafeInternalButton> buttonMap;
	private final ArrayList<String> device_ids, button_refs;

	public Database(ThreadingRobot bot) {
		robot = bot;
		device_ids = getDeviceIDs();
		button_refs = getButtonRefs();

		HashMap<String, ThreadSafeHolder> tempMap = new HashMap<>();
		deviceMap = Collections.synchronizedMap(tempMap);
		fillDeviceMap();
		buttonMap = Collections.synchronizedMap(new HashMap<>());
		fillButtonMap();

	}

	public ArrayList<String> getDeviceIDs() {
		if (device_ids == null) {
			return robot.setDeviceIDs();
		} else {
			return device_ids;
		}
	}

	public ArrayList<String> getButtonRefs() {
		return robot.setButtonRefs();
	}

	private void fillDeviceMap() {
		for (String key : device_ids) {
			deviceMap.put(key, new ThreadSafeHolder());
		}
	}

	private void fillButtonMap() {
		for (String key : button_refs) {
			buttonMap.put(key, new ThreadSafeInternalButton());
		}
	}

	public double getValue(String key) {
		return deviceMap.get(key).getValue();
	}

	public void setValue(String key, double val) {
		deviceMap.get(key).setValue(val);
	}

	public synchronized Button getButton(String ref) {
		return buttonMap.get(ref);
	}

	public synchronized void setButtonValue(String ref, boolean newValue) {
		buttonMap.get(ref).setPressed(newValue);
	}
}

class ThreadSafeHolder {

	private volatile double value;
	private ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

	public double getValue() {
		try {
			lock.readLock().lock();
			return value;
		} finally {
			lock.readLock().unlock();
		}

	}

	public void setValue(double newValue) {
		try {
			lock.writeLock().lock();
			value = newValue;
		} finally {
			lock.writeLock().unlock();
		}
	}
}

class ThreadSafeInternalButton extends InternalButton {

	@Override
	public synchronized void setInverted(boolean inverted) {
		// TODO Auto-generated method stub
		super.setInverted(inverted);
	}

	@Override
	public synchronized void setPressed(boolean pressed) {
		// TODO Auto-generated method stub
		super.setPressed(pressed);
	}

	@Override
	public synchronized boolean get() {
		// TODO Auto-generated method stub
		return super.get();
	}

	@Override
	public synchronized void whenPressed(Command command) {
		// TODO Auto-generated method stub
		super.whenPressed(command);
	}

	@Override
	public synchronized void whileHeld(Command command) {
		// TODO Auto-generated method stub
		super.whileHeld(command);

	}

	@Override
	public synchronized void whenReleased(Command command) {
		// TODO Auto-generated method stub
		super.whenReleased(command);
	}

	@Override
	public synchronized void toggleWhenPressed(Command command) {
		// TODO Auto-generated method stub
		super.toggleWhenPressed(command);
	}

	@Override
	public synchronized void cancelWhenPressed(Command command) {
		// TODO Auto-generated method stub
		super.cancelWhenPressed(command);
	}

}