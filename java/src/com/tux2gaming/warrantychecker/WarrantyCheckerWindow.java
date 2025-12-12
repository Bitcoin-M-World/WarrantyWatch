package com.tux2gaming.warrantychecker;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.tux2gaming.warrantychecker.threads.ParseSerials;

public class WarrantyCheckerWindow extends JFrame implements ActionListener, PropertyChangeListener {
	private static final long serialVersionUID = -8591640247638500057L;
	static String name = "Cryptominer Warranty Checker";
	static String version = "1.0.9";

	JTextField sourcefolder = new JTextField(30);
	JTextField destinationfolder = new JTextField(30);
	JButton sourcebrowse = new JButton("Browse...");
	JButton destinationbrowse = new JButton("Browse...");
	JComboBox<String> minersoption = new JComboBox<String>(new String[] {"Antminer", "Whatsminer", "Canaan", "Alpha Miner", "Auto"});
	JButton start = new JButton("Start");
	JProgressBar totalprogress = new JProgressBar();
	
	ParseSerials parser = null;
	private Thread convertthread = null;
	
	FileNameExtensionFilter filter = new FileNameExtensionFilter("Excel files", "xlsx");

	public WarrantyCheckerWindow() {
		super(name);
		Container contentPane = getContentPane();
		contentPane.setLayout(new BorderLayout());
		//JTabbedPane tabpane = new JTabbedPane(JTabbedPane.TOP);
		JPanel contents = new JPanel();
		contents.setLayout(new BoxLayout(contents, BoxLayout.PAGE_AXIS));
		JPanel source = new JPanel();
		source.setLayout(new BoxLayout(source, BoxLayout.LINE_AXIS));
		source.add(new JLabel("Source:"));
		sourcefolder.setEditable(false);
		source.add(sourcefolder);
		sourcebrowse.addActionListener(this);
		source.add(sourcebrowse);
		source.setMaximumSize(new Dimension(sourcefolder.getMaximumSize().width, sourcefolder.getMinimumSize().height + 3));
		source.setAlignmentX(LEFT_ALIGNMENT);
		contents.add(source);
		JPanel destination = new JPanel();
		destination.add(new JLabel("Destination:"));
		destination.setLayout(new BoxLayout(destination, BoxLayout.LINE_AXIS));
		destinationfolder.setEditable(false);
		destination.add(destinationfolder);
		destinationbrowse.addActionListener(this);
		destination.add(destinationbrowse);
		destination.setMaximumSize(new Dimension(destinationfolder.getMaximumSize().width, destinationfolder.getMinimumSize().height + 3));
		destination.setAlignmentX(LEFT_ALIGNMENT);
		contents.add(destination);
		JPanel minertypepanel = new JPanel();
		minertypepanel.setLayout(new BoxLayout(minertypepanel, BoxLayout.LINE_AXIS));
		minertypepanel.add(new JLabel("Miner Type: "));
		minersoption.setSelectedIndex(4);
		minertypepanel.add(minersoption);
		minertypepanel.setMaximumSize(new Dimension(minersoption.getMaximumSize().width, minersoption.getMinimumSize().height));
		minertypepanel.setAlignmentX(LEFT_ALIGNMENT);
		contents.add(minertypepanel);
		totalprogress.setString("Press \"Start\" to get warranty information");
		totalprogress.setStringPainted(true);
		contents.add(totalprogress);
		start.addActionListener(this);
		contents.add(start);
		contents.add(Box.createGlue());
		//tabpane.add("Warranty Checker", contents);

		//contentPane.add(tabpane, BorderLayout.CENTER);
		contentPane.add(contents, BorderLayout.CENTER);
		contentPane.add(new JLabel("Version " + version), BorderLayout.PAGE_END);
	}

	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			System.err.println("Could not set look and feel");
		}
		final WarrantyCheckerWindow f = new WarrantyCheckerWindow();
		f.setBounds(100, 100, 600, 300);
		f.setVisible(true);
		f.setDefaultCloseOperation(EXIT_ON_CLOSE);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if ("status".equals(evt.getPropertyName())) {
			String progressstring = evt.getNewValue().toString();
			totalprogress.setString(progressstring);
        }else if("done".equals(evt.getPropertyName())) {
			totalprogress.setIndeterminate(false);
			totalprogress.setMaximum(100);
			totalprogress.setValue(100);
			sourcebrowse.setEnabled(true);
			destinationbrowse.setEnabled(true);
			minersoption.setEnabled(true);
        	start.setText("Start");
        }else if ("total".equals(evt.getPropertyName())) {
			String totalstring = evt.getNewValue().toString();
			try {
				int totalitems = Integer.parseInt(totalstring);
				totalprogress.setIndeterminate(false);
				totalprogress.setMaximum(totalitems);
				totalprogress.setValue(0);
			}catch(NumberFormatException e) {
				e.printStackTrace();
			}
        }else if ("varat".equals(evt.getPropertyName())) {
			String atstring = evt.getNewValue().toString();
			try {
				int atitems = Integer.parseInt(atstring);
				totalprogress.setValue(atitems);
			}catch(NumberFormatException e) {
				e.printStackTrace();
			}
        }
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == sourcebrowse) {
			JFileChooser chooser = new JFileChooser();
			//chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
			chooser.setFileFilter(filter);
			if(!sourcefolder.getText().equals("")) {
				File source = new File(sourcefolder.getText()).getParentFile();
				chooser.setCurrentDirectory(source);
			}
			int result = chooser.showOpenDialog(this);
			File fileobj = chooser.getSelectedFile();
			if(result == JFileChooser.APPROVE_OPTION) {
				sourcefolder.setText(fileobj.getAbsolutePath());
			}
		}else if(e.getSource() == destinationbrowse) {
			JFileChooser chooser = new JFileChooser();
			//chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
			chooser.setFileFilter(filter);
			chooser.setDialogType(JFileChooser.SAVE_DIALOG);
			if(!destinationfolder.getText().equals("")) {
				File source = new File(destinationfolder.getText()).getParentFile();
				chooser.setCurrentDirectory(source);
			}else if(!sourcefolder.getText().equals("")) {
				File source = new File(sourcefolder.getText()).getParentFile();
				chooser.setCurrentDirectory(source);
			}
			int result = chooser.showSaveDialog(this);
			File fileobj = chooser.getSelectedFile();
			if(result == JFileChooser.APPROVE_OPTION) {
				String dest = fileobj.getAbsolutePath();
				if(!dest.toLowerCase().endsWith(".xlsx")) {
					dest += ".xlsx";
				}
				destinationfolder.setText(dest);
			}
		}else if(e.getSource() == start) {
			if(start.getText().equals("Start")) {
				if(sourcefolder.getText().equals(destinationfolder.getText()) || 
						(sourcefolder.getText().equals("") || destinationfolder.getText().equals(""))) {
					return;
				}
				sourcebrowse.setEnabled(false);
				destinationbrowse.setEnabled(false);
				start.setText("Cancel");
				totalprogress.setString("Please wait as we initialize your file...");
				totalprogress.setIndeterminate(true);
				MinerType mtype = null;
				minersoption.setEnabled(false);
				int selectedminer = minersoption.getSelectedIndex();
				switch (selectedminer) {
				case 0:
					mtype = MinerType.BITMAIN;
					break;
				case 1:
					mtype = MinerType.WHATSMINER;
					break;
				case 2:
					mtype = MinerType.CANAAN;
					break;
				case 3:
					mtype = MinerType.ALPHAMINER;
					break;
				case 4:
					mtype = MinerType.AUTO;
					break;

				default:
					mtype = MinerType.BITMAIN;
					break;
				}
				parser = new ParseSerials(sourcefolder.getText(), destinationfolder.getText(), mtype);
				parser.addPropertyChangeListener(this);
				convertthread = new Thread(parser);
				convertthread.start();
			}else if(start.getText().equals("Cancel")) {
				if(parser != null) {
					parser.stopIt();
				}
				totalprogress.setString("Warranty grabber process canceled.");
			}
		}
		
	}

}
