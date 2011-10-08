package de.jkitberatung.player;

import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;

import org.apache.jmeter.config.CSVDataSet;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.Interruptible;
import org.apache.jmeter.samplers.SampleResult;

import de.jkitberatung.ica.wsh.IICAClient;
import de.jkitberatung.ica.wsh.IKeyboard;
import de.jkitberatung.ica.wsh.IMouse;
import de.jkitberatung.ica.wsh.IScreenShot;
import de.jkitberatung.player.gui.PlaySamplerControlGui;
import de.jkitberatung.recorder.Interaction;
import de.jkitberatung.recorder.RecordingStep;
import de.jkitberatung.recorder.StringInteraction;
import de.jkitberatung.recorder.Interaction.Label;
import de.jkitberatung.util.IcaConnector;

public class PlaySampler extends AbstractSampler implements Interruptible{

	/**
	 * 
	 */
	private static final long serialVersionUID = 2454380067430756308L;

	private static final String SCREENSHOTS_PATH = "PlaySampler.screenshots_path";
	private static final String SCREENSHOTS_FOLDER_PATH = "PlaySampler.screenshots_folder_path";

	private static final String VALUES_SEPARATOR = ",";

	private static final String DEFAULT_SLEEP_TIME = "75";

	private transient RecordingStep crtPlayingStep;
	private transient IICAClient ica;
	private double sleepFactor;
	private RecordingStep step;
	private HashMap<String, String> stringInput;

	private boolean isInterrupted = false;

	private PlaySamplerControl samplerController;

	
	@Override
	public Object clone() {
		
		Object clone = super.clone();
		
		((PlaySampler) clone).setCrtPlayingStep(crtPlayingStep);
		((PlaySampler) clone).setIca(ica);

		if (null == samplerController) 
//			setController((PlaySamplerControl) IcaConnector.getInstance().getSamplerController().clone());		
			setController(IcaConnector.getInstance().getSamplerController());		
		
		((PlaySampler) clone).setController((PlaySamplerControl) samplerController.clone());
		
		((PlaySampler) clone).setSleepFactor(sleepFactor);
		((PlaySampler) clone).setStep(step);
		((PlaySampler) clone).setStringInput(stringInput);
		
		return clone;
	}

	public void setCrtPlayingStep(RecordingStep crtPlayingStep) {
		this.crtPlayingStep = crtPlayingStep;
	}

	public HashMap<String, String> getStringInput() {
		return stringInput;
	}

	public void setStringInput(HashMap<String, String> stringInput) {
		this.stringInput = stringInput;
	}

	public void setIca(IICAClient ica) {
		this.ica = ica;
	}

	public void setStep(RecordingStep step) {
		this.step = step;
	}

	public void setSleepFactor(double sleepFactor) {
		this.sleepFactor = sleepFactor;		
	}
	

	protected void playInteractions(PlaySampleResult result) {

		try {			
			if (null == samplerController) 
				setController((PlaySamplerControl) IcaConnector.getInstance().getSamplerController().clone());

			
			if (IcaConnector.getInstance().getIcaMap().containsKey(getThreadName()) &&
				IcaConnector.getInstance().getIcaMap().get(getThreadName()).session() != null) {			
				ica = IcaConnector.getInstance().getIcaMap().get(getThreadName());
				System.out.println("footprint#1");
			} else {
				if (step.isFirst()) {
					setController((PlaySamplerControl) samplerController.clone());
//					samplerController.getIcaConnector().setIca(null);
//					samplerController.getIcaConnector().setIcaLoggedOn(false);
					System.out.println(getThreadName() + ": " + "Forcing new connection...");
				}
				ica = samplerController.getIcaConnector().getIca();
				IcaConnector.getInstance().getIcaMap().put(getThreadName(), ica);
				System.out.println("footprint#2");
			}
			
			if (ica == null || ica.session() == null) {
				System.out.println(getThreadName() + ": " + "Failed playing interactions of step: " + step.getName());
				return;
			}
			
			IKeyboard keyboard = ica.session().keyboard();
			IMouse mouse = ica.session().mouse();

			crtPlayingStep = new RecordingStep(step.getName(), step.isAutoAddSleeptimes());
		 	
			List<Interaction> interactionList = step.getInteractionList();
			System.out.println(getThreadName() + ": " + "Playing step " + step.getName() + "...");

			setSleepFactor();
			
			Interaction lastMouseDown = null;

			for (Interaction interaction : interactionList) {
				if (ica == null || ica.session() == null)
					return;//abort playback due to session interruption
				
				if (isInterrupted) {
					result.setResponseMessage("Sampler interrupted !");
					return;
				}
				
				Label label = interaction.getLabel();
				
				if (label.equals(Interaction.Label.MouseDown))
					lastMouseDown = new Interaction(interaction.getLabel(), interaction.getValue());
				if (label.equals(Interaction.Label.StringTag))
					playStringInteraction(interaction, lastMouseDown, keyboard, mouse, crtPlayingStep.getName(), result);
				else 
					playInteraction(interaction, lastMouseDown, keyboard, mouse, result);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void setSleepFactor() {
		JCheckBox sleepingCheckBox = IcaConnector.getInstance().getSleepingCheckBox();
		if (!sleepingCheckBox.isSelected()) {
			sleepFactor = 0.00;
			return;
		}
		JComboBox sleepingComboBox = IcaConnector.getInstance().getSleepingComboBox();
		String sleepFactorStr = sleepingComboBox.getSelectedItem().toString();
		sleepFactor = sleepFactorStr.equals(PlaySamplerControlGui.DEFAULT_SLEEP_FACTOR) ? 1.00 : Double.valueOf(sleepFactorStr).doubleValue();
	}

	private void playStringInteraction(Interaction interaction,	Interaction lastMouseDown,
									   IKeyboard keyboard, IMouse mouse, 
									   String stepName, PlaySampleResult result) {
		
		System.out.println(getThreadName() + ": " + "Playing tag " + interaction.getValue());

		StringInteraction strInteraction = (StringInteraction) interaction;
		List<Interaction> interactions = strInteraction.getInteractions();

		if (null == stringInput)
			stringInput = IcaConnector.getInstance().getTagsMap().get(stepName);
		
		// Check if we need to play custom csv input  
		if (treeContainsCsvDataSetConfig() && tagIsCustomizable(strInteraction.getName()))
			interactions = buildInteractionsFromString(getTagInput(strInteraction.getName()));

		for (Interaction inter : interactions) 
			playInteraction(inter, lastMouseDown, keyboard, mouse, result);
	}

	private boolean tagIsCustomizable(String tagName) {
		if (stringInput.size() > 0 && 
			stringInput.containsKey(tagName) && 
			!stringInput.get(tagName).isEmpty())
			return true;
		return false;
	}

	private boolean treeContainsCsvDataSetConfig() {
        Enumeration<JMeterTreeNode> enumNode = threadGroupNode().children();
        while (enumNode.hasMoreElements()) {
            JMeterTreeNode child = enumNode.nextElement();
            if (child.getUserObject() instanceof CSVDataSet)
            	return true;
        }
        return false;
	}

	private JMeterTreeNode threadGroupNode() {
        JMeterTreeModel treeModel = GuiPackage.getInstance().getTreeModel();
		JMeterTreeNode myNode = treeModel.getNodeOf(IcaConnector.getInstance().getSamplerController());
		if (myNode != null)
			return (JMeterTreeNode) myNode.getParent();
		return null;
    }

	private List<Interaction> buildInteractionsFromString(String string) {
//		System.out.println("Building interations from sting: " + string);		
		List<Interaction> list = new ArrayList<Interaction>();
		for (int i = 0; i < string.length(); i++) {
			int[] keyIds = charToKeyId(string.codePointAt(i));
			list.add(new Interaction(Interaction.Label.KeyDown, keyIds[0] + ""));//keyDown ALT
			list.add(new Interaction(Interaction.Label.SleepTime, DEFAULT_SLEEP_TIME));//sleeptime
			for (int j = 1; j < keyIds.length; j++) {
				list.add(new Interaction(Interaction.Label.KeyDown, keyIds[j] + ""));
				list.add(new Interaction(Interaction.Label.SleepTime, DEFAULT_SLEEP_TIME));//sleeptime
				list.add(new Interaction(Interaction.Label.KeyUp, keyIds[j] + ""));
				list.add(new Interaction(Interaction.Label.SleepTime, DEFAULT_SLEEP_TIME));//sleeptime
			}
			list.add(new Interaction(Interaction.Label.KeyUp, keyIds[0] + ""));//keyUp ALT
			list.add(new Interaction(Interaction.Label.SleepTime, DEFAULT_SLEEP_TIME));//sleeptime
		}
		return list;
	}

	private void playInteraction(Interaction interaction, Interaction lastMouseDown,
								 IKeyboard keyboard, IMouse mouse, 
								 PlaySampleResult result) {
		String[] tokens = null;
		Label label = interaction.getLabel();
		System.out.println("  playing " + interaction);
		switch (label) {
			case KeyUp:
				keyboard.sendKeyUp(Integer.parseInt((String) interaction.getValue()));
				break;
			case KeyDown:
				keyboard.sendKeyDown(Integer.parseInt((String) interaction.getValue()));
				break;
	
			case MouseDown:
				tokens = ((String)interaction.getValue()).split(",");
				mouse.sendMouseDown(Integer.parseInt(tokens[2]), Integer.parseInt(tokens[3]), Integer.parseInt(tokens[0]) , Integer.parseInt(tokens[1]));
				break;
	
			case MouseUp:
				tokens = ((String)interaction.getValue()).split(",");
				mouse.sendMouseUp(Integer.parseInt(tokens[2]), Integer.parseInt(tokens[3]), Integer.parseInt(tokens[0]) , Integer.parseInt(tokens[1]));
				break;
	
			case MouseDoubleClick:
				if (null != lastMouseDown) {
					tokens = ((String)lastMouseDown.getValue()).split(",");
					mouse.sendMouseDown(Integer.parseInt(tokens[2]), Integer.parseInt(tokens[3]), Integer.parseInt(tokens[0]) , Integer.parseInt(tokens[1]));
					mouse.sendMouseUp(Integer.parseInt(tokens[2]), Integer.parseInt(tokens[3]), Integer.parseInt(tokens[0]) , Integer.parseInt(tokens[1]));
					mouse.sendMouseDown(Integer.parseInt(tokens[2]), Integer.parseInt(tokens[3]), Integer.parseInt(tokens[0]) , Integer.parseInt(tokens[1]));
					mouse.sendMouseUp(Integer.parseInt(tokens[2]), Integer.parseInt(tokens[3]), Integer.parseInt(tokens[0]) , Integer.parseInt(tokens[1]));
				}
				break;
	
			case ScreenShot:
				String value = (String)interaction.getValue();
				String[] split = value.split(VALUES_SEPARATOR);
				result.addHash(split[0], true);
				takeScreenShot(true,  split[1], split[2], split[3], split[4], result);
				break;
	
			case SleepTime:
				try {
					System.out.println(" sleeping with factor " + sleepFactor);
					Thread.sleep((long) (Integer.parseInt((String) interaction.getValue()) * sleepFactor));
				} catch (NumberFormatException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					System.out.println(getThreadName() + ": " + "Sleep interrupted");
				}
				break;
	
			default:
				break;
		}		
	}


	private int[] charToKeyId(int codePoint) {
		int[] keyIds = null;
		String pStr = Integer.valueOf(codePoint).toString();//code point for 'ü' is 252; we turn it into "252"
		keyIds = new int[pStr.length() + 2];//keyIds must hold <ALT><NUMPAD0><NUMPAD2><NUMPAD5><NUMPAD2> to generate 'ü'
		keyIds[0] = KeyEvent.VK_ALT;
		keyIds[1] = KeyEvent.VK_NUMPAD0;
		for (int index = 0; index < pStr.length(); index++)
			keyIds[index + 2] = KeyEvent.VK_NUMPAD0 +  Integer.valueOf(pStr.substring(index,index+1)).intValue();

		return keyIds;
	}	

	public void takeScreenShot(boolean saveBitmap, 
							   String x, String y, 
							   String width, String height, 
							   PlaySampleResult result) {
		
		IScreenShot ss = ica.session().createScreenShot(Integer.parseInt(x) , Integer.parseInt(y),
														Integer.parseInt(width), Integer.parseInt(height));
		System.out.println(getThreadName() + ": " + "Creating a screenshot... Hash value " + ss.bitmapHash());
		crtPlayingStep.addInteraction(new Interaction(Interaction.Label.ScreenShot, ss.bitmapHash()));
		result.addHash(ss.bitmapHash(), false);
		if (saveBitmap) {
			ss.filename(IcaConnector.getInstance().getLocationHashFolder() + 
						System.getProperty("file.separator") + 
						System.currentTimeMillis() + ".bmp");
			ss.save();
			System.out.println(getThreadName() + ": " + "Saving screenshot to file " + ss.filename());
		}
	}

	@Override
	public SampleResult sample(Entry e) {
		if (step == null) {
			String stepName = getName().substring(0,getName().indexOf(" Sampler"));
			setStep(IcaConnector.getInstance().getStepByName(stepName));
			setTagsInput(IcaConnector.getInstance().getTagsMap().get(stepName));
		}
		
		System.out.println(getThreadName() + ": " + "Sampling step " + step.getName() + "...");
		
		PlaySampleResult result = new PlaySampleResult();
		result.setSampleLabel(step.getName());
		result.sampleStart();

		Toolkit.getDefaultToolkit().setLockingKeyState(KeyEvent.VK_NUM_LOCK, true);
		playInteractions(result);
		if (!isInterrupted)
			dumpScreenshotsHashes(result);
		
		result.sampleEnd();
		
		System.out.println(getThreadName() + ": " + "Sampling step " + step.getName() + " ended.");
		
		if (step.isLast()) {
//			samplerController.getIcaConnector().setIca(null);
			IcaConnector.getInstance().getIcaMap().remove(getThreadName());
			System.out.println(getThreadName() + ":" + "Removed entry from ICA connections map");
		}
		
		return result;
	}

	private void setTagsInput(LinkedHashMap<String, String> tags) {
		for (String tagName : tags.keySet())
			setTagInput(tagName, tags.get(tagName));		
	}

	private synchronized void dumpScreenshotsHashes(PlaySampleResult result) {
		System.out.println(getThreadName() + ": " + "Dumping hash value to file " + IcaConnector.getInstance().getLocationHashFile());
		if (crtPlayingStep == null) {
			System.out.println(" failed: crtPlayingStep is null");
			return;
		}
		File file = new File(IcaConnector.getInstance().getLocationHashFile());
		FileWriter fw;
		try {
			fw = new FileWriter(file, true);
			fw.write("\n" + crtPlayingStep.toString());
			fw.flush();
			fw.close();
		} catch (IOException e) {
			System.out.println(getThreadName() + ": " + "Failed dumping hash value to file \n" + e.getMessage());
			e.printStackTrace();
		}
	}	

	
	@Override
	/**
	 * Interrupt the sampling (When user hits Stop on the test)
	 */
	public boolean interrupt() {
		isInterrupted = true;
		System.out.println(getThreadName() + ": " + "Player interrupted");
		return true;
	}


	public void setScreenshotsHashesFilePath(String path) {
		setProperty(SCREENSHOTS_PATH, path);
	}

	public String getScreenshotsHashesFilePath() {
		return getPropertyAsString(SCREENSHOTS_PATH);
	}

	public void setScreenshotsForderPath(String path) {
		setProperty(SCREENSHOTS_FOLDER_PATH, path);
	}

	public String getScreenshotsFolderPath() {
		return getPropertyAsString(SCREENSHOTS_FOLDER_PATH);
	}

	public void setTagInput(String tagName, String tagInput) {
		setProperty(tagName, tagInput);
	}
	
	public String getTagInput(String tagName) {
		return getPropertyAsString(tagName);
	}

	public void setController(PlaySamplerControl samplerController) {
		this.samplerController = samplerController;
	}
}
