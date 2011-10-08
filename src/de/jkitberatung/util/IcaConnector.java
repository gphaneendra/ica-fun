package de.jkitberatung.util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;

import de.jkitberatung.ica.wsh.IICAClient;
import de.jkitberatung.ica.wsh.IWindows;
import de.jkitberatung.ica.wsh.events._IICAClientEvents;
import de.jkitberatung.player.PlaySamplerControl;
import de.jkitberatung.recorder.RecordingStep;


public class IcaConnector {

	private IICAClient ica;
	private static IcaConnector instance;
	private PlaySamplerControl samplerController;

	private boolean isICALoggedOn;
	private String app;
	private String address;
	private String port;
	private String domain;
	private String username;
	private String password;
	private double sleepingTimeFactor;
	private String locationHashFile;
	private String locationHashFolder;
	private String runningMode;

	private List<RecordingStep> steps;
	private LinkedHashMap<String, LinkedHashMap<String,String>> tagsMap;

	private JCheckBox jckbSleepCheckBox;
	private JComboBox jcbSleepComboBox;

	boolean error = false;
	private HashMap<String, IICAClient> icaMap;

	
	public double getSleepingTimeFactor() {
		return sleepingTimeFactor;
	}

	public void setSleepingTimeFactor(double sleepingTimeFactor) {
		this.sleepingTimeFactor = sleepingTimeFactor;
	}

	public String getLocationHashFile() {
		return locationHashFile;
	}

	public void setLocationHashFile(String locationHashFile) {
		this.locationHashFile = locationHashFile;
	}

	public String getLocationHashFolder() {
		return locationHashFolder;
	}

	public void setLocationHashFolder(String locationHashFolder) {
		this.locationHashFolder = locationHashFolder;
	}

	
	public IcaConnector() {
		ica = null;
	}

	public static IcaConnector getInstance(){
		if (instance == null)
			instance = new IcaConnector();

		return instance; 
	}
	

	public static void resetInstance() {
		instance = null;
	}

	private void initAndConnect() {

		init();
		connect();
		
	}
	
	private void init() {		
		ica = ICAInitializer.initICASession(getInitialApp(), getIcaAddress(), getPort(), 
											getDomain(), getUsername(), getPassword(),
											getRunningMode());
	}

	private String getRunningMode() {
		return runningMode;
	}

	private String getPassword() {
		return password;
	}

	private String getUsername() {
		return username;
	}

	private String getDomain() {
		return domain;
	}

	private String getPort() {
		return port;
	}

	private String getIcaAddress() {
		return address;
	}

	private String getInitialApp() {
		return app;
	}

	
	
	public void setApp(String app) {
		this.app = app;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public void setPort(String port) {
		this.port = port;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	private void connect() {
		//Add listeners to ICA Client
		setIcaClientListener();
		// Connect to our session
		System.out.println("Connecting to server...");
		ica.connect();		
	}
	
	private void setIcaClientListener() {

		ica.advise(_IICAClientEvents.class, new _IICAClientEvents() {

			public void onLogon() {
				// Right now the session is not null anymore

				System.out.println("ICAClientEvent: Logged on.");
				System.out.println("ICAClientEvent: ICA session: " + ica.session());

				ica.session().replayMode(true);
				
				isICALoggedOn = true;

				try {
					IWindows windows = ica.session().topLevelWindows();
					System.out.println("ICAClientEvent: TopLevelWindow count:" + windows.count());

				}catch (Throwable e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onConnect() {
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
				isICALoggedOn = false;
				ica = null;
			}

			@Override
			public void onDisconnectFailed() {
				System.out.println("ICAClientEvent: Disconnecting failed");
				error = true;
//				JOptionPane.showMessageDialog(null, "Failed disconnecting from the server", "Error", JOptionPane.ERROR_MESSAGE);
			}

			@Override
			public void onLogonFailed() {
				System.out.println("ICAClientEvent: Logging-in failed");
				error = true;
//				JOptionPane.showMessageDialog(null, "Error logging into the server", "Error", JOptionPane.ERROR_MESSAGE);
			}

			@Override
			public void onConnectFailed() {
				System.out.println("ICAClientEvent: Connecting failed");
				error = true;
//				JOptionPane.showMessageDialog(null, "Error connecting to the server", "Error", JOptionPane.ERROR_MESSAGE);
			}
		});

	}
	
	
	public IICAClient getIca() {
		
		if (ica == null || ica.session() == null)
			initAndConnect();
		
		// Wait until ica session becomes available
		while (!isICALoggedOn && !error) {
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				System.out.println("Interrupting connection attempt loop... done.");
			}
		}
		
		return ica;
	}
	
	public void setIca(IICAClient ica) {
		this.ica = ica;
	}
	
	public void setIcaLoggedOn(boolean b) {
		this.isICALoggedOn = b;
	}

	public RecordingStep getStepByName(String stepName) {
		for (RecordingStep step : steps) 
			if (step.getName().equals(stepName))
				return step;
		return null;
	}
	
	public void setSteps(List<RecordingStep> steps) {
		this.steps = steps;
	}

	public void setSleepingCheckBox(JCheckBox jckbSleepCheckBox) {
		this.jckbSleepCheckBox = jckbSleepCheckBox;
	}

	public void setSleepingComboBox(JComboBox jcbSleepComboBox) {
		this.jcbSleepComboBox = jcbSleepComboBox;
	}

	public void setRunningMode(String runningMode) {
		this.runningMode = runningMode;
	}

	public JCheckBox getSleepingCheckBox() {
		return jckbSleepCheckBox;
	}

	public JComboBox getSleepingComboBox() {
		return jcbSleepComboBox;
	}

	public void setTagsMap(LinkedHashMap<String, LinkedHashMap<String,String>> tagsMap) {
		this.tagsMap = tagsMap;
	}

	public LinkedHashMap<String, LinkedHashMap<String, String>> getTagsMap() {
		return tagsMap;
	}

	public void setSamplerController(PlaySamplerControl samplerController) {
		this.samplerController = samplerController;
	}
	
	public PlaySamplerControl getSamplerController() {
		return samplerController;
	}

	public HashMap<String, IICAClient> getIcaMap() {
		if (null == icaMap)
			icaMap = new HashMap<String, IICAClient>();
		return icaMap;
	}
	
	public void setIcaMap(HashMap<String, IICAClient> map) {
		icaMap = map;
	}
}
