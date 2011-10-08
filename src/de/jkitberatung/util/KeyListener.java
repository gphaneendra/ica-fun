package de.jkitberatung.util;

import java.awt.event.KeyEvent;
import javax.swing.JTextField;
import org.apache.jmeter.gui.AbstractJMeterGuiComponent;

import de.jkitberatung.util.gui.GuiUtil;

public class KeyListener implements java.awt.event.KeyListener {
	
	private AbstractJMeterGuiComponent jMeterComponent;

	public KeyListener(AbstractJMeterGuiComponent jMeterComponent) {
		this.jMeterComponent = jMeterComponent;
	}

	@Override
	public void keyPressed(KeyEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void keyReleased(KeyEvent e) {
		JTextField field = (JTextField) e.getSource();
		if (field.getToolTipText().equals(GuiUtil.TOOLTIP_NAME)) 
			jMeterComponent.setName(field.getText().trim());		
		else if (field.getToolTipText().equals(GuiUtil.TOOLTIP_COMMENT)) 
			jMeterComponent.setComment(field.getText().trim());		
	}

	@Override
	public void keyTyped(KeyEvent arg0) {
		// TODO Auto-generated method stub

	}

}
