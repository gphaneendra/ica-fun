package de.jkitberatung.recorder;


import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import org.apache.jmeter.control.GenericController;

import de.jkitberatung.ica.wsh.IICAClient;
import de.jkitberatung.ica.wsh.IScreenShot;
import de.jkitberatung.ica.wsh.IWindow;
import de.jkitberatung.ica.wsh.IWindows;
import de.jkitberatung.ica.wsh.events._IICAClientEvents;
import de.jkitberatung.ica.wsh.events._IKeyboardEvents;
import de.jkitberatung.ica.wsh.events._IMouseEvents;
import de.jkitberatung.ica.wsh.events._ISessionEvents;
import de.jkitberatung.ica.wsh.events._IWindowEvents;
import de.jkitberatung.recorder.gui.RecordControlGui;
import de.jkitberatung.util.ICAInitializer;



public class RecordControl extends GenericController {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	//	@Override

	private static final String ADDRESS = "RecordControl.ica_address";
	private static final String BITMAP_FOLDER = "RecordControl.bitmap_folder";	
	private static final String DOMAIN = "RecordControl.domain";
	private static final String INITIAL_APP = "RecordControl.initial_app";
	private static final String PASSWORD = "RecordControl.password";
	private static final String PORT = "RecordControl.port";
	private static final String USERNAME = "RecordControl.username";

	private transient IICAClient ica;
	private transient RecordingStep crtStep;

	private RecordControlGui recorderGuiHandler;
	private List<RecordingStep> steps;

	private boolean recording;
	private boolean followedByConnect;


	public void record() {
		new Thread(new Runnable() {
			@Override
			public void run() {				
				if (null != ica && ica.isConnected()) {
					System.out.println("Disconnecting...");
					followedByConnect = true;
					disconnect();
					return;
				}
				init();
				connect();
			}
		}).start();
	}

	public void init() {
		recording = true;
		followedByConnect = false;
//		System.out.println("Initialising connection...");
		ica = ICAInitializer.initICASession(getInitialApp(), getIcaAddress(), getPort(), 
											getDomain(), getUsername(), getPassword(), "NORMAL");
	}

	private void connect() {		
		//Add listeners to ICA Client
		setIcaClientListener();		
		// Connect to our session
		System.out.println("Connecting to server...");
		ica.connect();		
	}

	private void disconnect() {
		if (!ica.isConnected()) 
			return;

		ica.logoff();
		System.out.println("ICA logged off");

		ica.disconnect();
		System.out.println("ICA disconected");
	}

	public void setDomain(String domain) {
		setProperty(DOMAIN, domain);
	}

	public void setPort(String port) {
		setProperty(PORT, port);
	}

	public String getDomain() {
		return getPropertyAsString(DOMAIN);
	}

	public String getPort() {
		return getPropertyAsString(PORT);
	}

	/**
	 * Create new Step in crt recording session
	 * @param name
	 * @param autoAddSleeptimes 
	 */
	public void newStep(String name, boolean autoAddSleeptimes) {
		crtStep = new RecordingStep(name, autoAddSleeptimes);

		steps.add(crtStep);
		System.out.println("New step created:" + name);
	}

	public String getPassword() {
		return getPropertyAsString(PASSWORD);
	}

	public String getUsername() {
		return getPropertyAsString(USERNAME);
	}

	public String getIcaAddress() {		
		return getPropertyAsString(ADDRESS);
	}

	public String getInitialApp() {
		return getPropertyAsString(INITIAL_APP);
	}

	public void setInitialApp(String initialApp) {
		setProperty(INITIAL_APP, initialApp);
	}

	public void setIcaAddress(String icaAddress) {
		setProperty(ADDRESS, icaAddress);
	}

	public void setUsername(String username) {
		setProperty(USERNAME, username);
	}

	public void setPassword(String password) {
		setProperty(PASSWORD, password);
	}

	public void stop() {
		recording = false;
		System.out.println("Stopped recording session.");
	}

	public void takeScreenShot(boolean saveBitmap) {
		IScreenShot ss = ica.session().createFullScreenShot();

		String path = this.getBitmapFolder() + System.getProperty("file.separator") + "fullscreenshot" + ".bmp";
		ss.filename(path );			
		ss.save();
		System.out.println("Saved Full ScreenShot to file: " + ss.filename());


		ScreenshotAreaSelector areaSelector = new ScreenshotAreaSelector(path);
		Rectangle rectangle = areaSelector.getSelectedArea();

		IScreenShot ssPart = ica.session().createScreenShot(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
		System.out.println("Screenshot hash " + ssPart.bitmapHash());
		crtStep.addInteraction(new Interaction(Interaction.Label.ScreenShot, ssPart.bitmapHash() + "," + rectangle.x + "," + rectangle.y + "," + rectangle.width + "," + rectangle.height));

		if (saveBitmap) {
			ssPart.filename(this.getBitmapFolder() + 
					System.getProperty("file.separator") + 
					System.currentTimeMillis() + ".bmp");			
			ssPart.save();
			System.out.println("Saved ScreenShot to file: " + ssPart.filename());
		}
	}


	public String getBitmapFolder() {
		return getPropertyAsString(BITMAP_FOLDER);	
	}

	private void dumpInteraction(String filePath) {

		File file = new File(filePath != null ? filePath : "c:/record_" + System.currentTimeMillis() + ".txt");
		FileWriter fw;
		try {
			fw = new FileWriter(file, false);

			for (int i = 0; i < steps.size(); i++) {
				fw.write("\n" + steps.get(i).toString());
			}

			fw.flush();
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}

		System.out.println("Dumped interactions to file");
	}

	public void save(File file) {
		dumpInteraction(file.getAbsolutePath());
	}

	protected void setIcaSessionListeners() {
		setSessionListener();

		setKeyboardListener();

		setMouseListener();


	}

	private void setWindowListener(final IWindow wnd) {
		System.out.println("Set listener for window:" + wnd);

		wnd.advise(_IWindowEvents.class, new _IWindowEvents() {
			@Override
			public void onActivate() {
				System.out.println("OnActivate" + wnd.windowID());
				//				crtStep.addInteraction(new Interaction(Interaction.Label.WndActivate, ""));
				super.onActivate();
			}

			@Override
			public void onDeactivate() {
				System.out.println("OnDeactivate" + wnd.windowID());
				//				crtStep.addInteraction(new Interaction(Interaction.Label.WndDeactivate, ""));
				super.onDeactivate();
			}

			@Override
			public void onDestroy() {
				System.out.println("onDestroy" + wnd.windowID());
				//				crtStep.addInteraction(new Interaction(Interaction.Label.WndDestroy, ""));
				super.onDestroy();
			}

			@Override
			public void onMinimize() {
				System.out.println("onMinimize" + wnd.windowID());
				//				crtStep.addInteraction(new Interaction(Interaction.Label.WndMinimize, ""));
				super.onMinimize();
			}

			@Override
			public void onMove(int xPos, int yPos) {
				System.out.println("OnMove" + xPos + "," + yPos + "," + wnd.windowID());
				//				crtStep.addInteraction(new Interaction(Interaction.Label.WndMove, xPos + "," + yPos));
				super.onMove(xPos, yPos);
			}

			@Override
			public void onSize(int width, int height) {
				System.out.println("onSize" + wnd.windowID());
				//				crtStep.addInteraction(new Interaction(Interaction.Label.WndMove, width + "," + height));
				super.onSize(width, height);
			}
			@Override
			public void onCaptionChange(String caption) {
				System.out.println("onCaptureChange" + wnd.windowID());
				//				crtStep.addInteraction(new Interaction(Interaction.Label.WndCaptionChange, ""));
				super.onCaptionChange(caption);
			}
		});

	}

	private void setSessionListener() {
		ica.session().advise(_ISessionEvents.class, new _ISessionEvents() {
			@Override
			public void onWindowCreate(IWindow window) {
				System.out.println("SessionEvents: Window created:" + window.caption() + "," + window.windowID());

				setWindowListener(window);

				//				crtStep.addInteraction(new Interaction(Interaction.Label.SessWndCreate, window.caption()));
				super.onWindowCreate(window);
			}

			@Override
			public void onPingAck(String pingInfo, int roundTripTime) {
				System.out.println("SessionEvents: Ping ack:" + pingInfo + "," + roundTripTime);
				super.onPingAck(pingInfo, roundTripTime);
			}

			@Override
			public void onWindowDestroy(IWindow window) {
				System.out.println("SessionEvents: Window destroyed:" + window.caption());
				//				crtStep.addInteraction(new Interaction(Interaction.Label.SessWndDestroy, window.caption()));
				super.onWindowDestroy(window);
			}

			@Override
			public void onWindowForeground(int windowID) {
				System.out.println("SessionEvents: Window foreground:" + windowID);
				//				crtStep.addInteraction(new Interaction(Interaction.Label.SessWndForeground, windowID + ""));

				super.onWindowForeground(windowID);
			}

		});
	}

	private void setMouseListener() {
		ica.session().mouse().advise(_IMouseEvents.class, new _IMouseEvents() {
			@Override
			public void onMove(int buttonState, int modifierState, int xPos, int yPos) {
				super.onMove(buttonState, modifierState, xPos, yPos);
			}

			@Override
			public void onMouseDown(int buttonState, int modifierState,	int xPos, int yPos) {
				if (!recording)
					return;
				//				System.out.println(Interaction.Label.MouseDown.name() + xPos + "," + yPos + "," + buttonState + "," + modifierState);
				crtStep.addInteraction(new Interaction(Interaction.Label.MouseDown, xPos + "," + yPos + "," + buttonState + "," + modifierState));
				super.onMouseDown(buttonState, modifierState, xPos, yPos);
			}

			@Override
			public void onMouseUp(int buttonState, int modifierState, int xPos, 	int yPos) {
				if (!recording)
					return;
				//				System.out.println(Interaction.Label.MouseUp.name() + xPos + "," + yPos + "," + buttonState + "," + modifierState);
				crtStep.addInteraction(new Interaction(Interaction.Label.MouseUp, xPos + "," + yPos + "," + buttonState + "," + modifierState));
				super.onMouseUp(buttonState, modifierState, xPos, yPos);
			}

			@Override
			public void onDoubleClick() {
				if (!recording)
					return;
				//				System.out.println(Interaction.Label.MouseDoubleClick.name());
				crtStep.addInteraction(new Interaction(Interaction.Label.MouseDoubleClick, ""));
				super.onDoubleClick();
			}
		});
	}

	private void setKeyboardListener() {
		ica.session().keyboard().advise(_IKeyboardEvents.class, new _IKeyboardEvents() {
			@Override
			public void onKeyUp(int keyId, int modifierState) {
				if (!recording)
					return;
				System.out.println(Interaction.Label.KeyUp.name() + keyId + "(" + KeyEvent.getKeyModifiersText(modifierState) + ")" + KeyEvent.getKeyText(keyId));
				System.out.println(keyId + " - " + modifierState);
				crtStep.addInteraction(new Interaction(Interaction.Label.KeyUp, keyId + "" /*(" + KeyEvent.getKeyModifiersText(modifierState) + ")" + KeyEvent.getKeyText(keyId)*/));
				super.onKeyUp(keyId, modifierState);
			}

			@Override
			public void onKeyDown(int keyId, int modifierState) {
				if (!recording)
					return;
				//				System.out.println(Interaction.Label.KeyDown.name() + keyId + "(" + KeyEvent.getKeyModifiersText(modifierState) + ")" + KeyEvent.getKeyText(keyId));
				crtStep.addInteraction(new Interaction(Interaction.Label.KeyDown, keyId + "" /*"(" + KeyEvent.getKeyModifiersText(modifierState) + ")" + KeyEvent.getKeyText(keyId)*/));
				super.onKeyDown(keyId, modifierState);
			}
		});
	}

	private void setIcaClientListener() {

		ica.advise(_IICAClientEvents.class, new _IICAClientEvents() {
			public void onLogon() {
				System.out.println("ICAClientEvent: Logged on.");

				System.out.println("ICAClientEvent: ICA session: " + ica.session());

				// Right now the session is not null anymore
				// so we can hook listeners to it
				setIcaSessionListeners();

				try {
					IWindows windows = ica.session().topLevelWindows();
					System.out.println("ICAClientEvent: TopLevelWindow count:" + windows.count());

				}catch (Exception e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onConnect() {
				recorderGuiHandler.onConnect();
				super.onConnect();
			}

			@Override
			public void onWindowCreated(int wndType, int xPos, int yPos,
					int width, int height) {
				System.out.println("ICAClientEvent: Window created (type):" + wndType);
				super.onWindowCreated(wndType, xPos, yPos, width, height);
			}

			@Override
			public void onWindowDisplayed(int wndType) {
				System.out.println("ICAClientEvent: Window displayed (type):" + wndType);
			}

			@Override
			public void onWindowCloseRequest() {
				System.out.println("ICAClientEvent: OnWindow close request");
			}

			@Override
			public void onDisconnect() {
				System.out.println("ICAClientEvent: onDisconnect");
				//				JOptionPane.showMessageDialog(null, "Disconnected from the server", "Info", JOptionPane.INFORMATION_MESSAGE);
				if (followedByConnect) {
					System.out.println("onDisconnect followed by call to record()");
					record();
					return;
				}
				System.out.println("onDisconnect followed by call to stop()");
				recorderGuiHandler.stop();
			}

			@Override
			public void onDisconnectFailed() {
				System.out.println("ICAClientEvent: Disconnecting failed");
				JOptionPane.showMessageDialog(null, "Failed disconnecting from the server", "Error", JOptionPane.ERROR_MESSAGE);
				recorderGuiHandler.stop();
			}

			@Override
			public void onLogonFailed() {
				System.out.println("ICAClientEvent: Error logging in");
				JOptionPane.showMessageDialog(null, "Error logging into the server", "Error", JOptionPane.ERROR_MESSAGE);
				recorderGuiHandler.stop();
			}

			@Override
			public void onConnectFailed() {
				System.out.println("ICAClientEvent: Connecting failed");
				JOptionPane.showMessageDialog(null, "Error connecting to the server", "Error", JOptionPane.ERROR_MESSAGE);
				recorderGuiHandler.stop();
			}
		});

	}

	public void renameStep(int index, String newName) {
		RecordingStep recordingStep = steps.get(index);
		if (recordingStep != null)
			recordingStep.setName(newName);
		else
			JOptionPane.showMessageDialog(null, "Can't find the step to be renamed!", "Error", JOptionPane.ERROR_MESSAGE);
	}

	public void deleteStep(int index) {
		if (index >= 0 && index < steps.size())
			steps.remove(index);
	}

	public void setGuiHandler(RecordControlGui handler) {
		System.out.println("Setting recorder's gui handler...");
		this.recorderGuiHandler = handler;
	}

	public void setBitmapFolder(String bitmapFolder) {
		setProperty(BITMAP_FOLDER, bitmapFolder);
	}

	public void addStringTagStart(String name) {
		crtStep.addInteraction(new Interaction(Interaction.Label.StringTagStart, name));
		System.out.println("Started tag " + name);
	}

	public void addStringTagEnd(String name) {
		crtStep.addInteraction(new Interaction(Interaction.Label.StringTagEnd, name));
		System.out.println("Ended tag " + name);
	}

	public void deleteStringTag(String name) {

		for (RecordingStep step : steps) {
			List<Interaction> list = step.getInteractionList();

			Interaction interactionToBeRemoved = null;
			for (Interaction inter : list) {
				if ((inter.getLabel().equals(Interaction.Label.StringTagStart) ||
						inter.getLabel().equals(Interaction.Label.StringTagEnd)) && ((String)inter.getValue()).equals(name) ) {
					interactionToBeRemoved = inter;
					break;
				}
			}

			// If found -> delete
			if (interactionToBeRemoved != null)
				list.remove(interactionToBeRemoved);
		}

	}

	public void clearSteps() {
		steps = new ArrayList<RecordingStep>();
	}
}
