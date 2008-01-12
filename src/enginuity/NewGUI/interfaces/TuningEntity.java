package enginuity.NewGUI.interfaces;

import javax.swing.JMenu;
import javax.swing.JToolBar;
import java.awt.event.ActionListener;
import java.util.Vector;


public interface TuningEntity extends ActionListener{
	
	// Return name of tuning entity
	public String getName();
	
	// Return all the menu items relevant to tuning entity
	public Vector<JMenu> getMenuItems();
	
	// Return the toolbar
	public JToolBar getToolBar();
	
	// Return object data based on passed table name
	public Object[][] getTableData(String tableName);
	
	// Remove tuning group
	public void removeTuningGroup(String tuningGroup);
	
	// Push back modified data to the tuning entity
	public void setTableData(String tableIdentifier, Object[][] data);
	
	// Control methods
	public void init(TuningEntityListener listener);
	
	// Notify of system exit. Tuning entity must reply to parent GUI that they are in fac
	// ready for shutdown
	public void notifySystemExit();
}
