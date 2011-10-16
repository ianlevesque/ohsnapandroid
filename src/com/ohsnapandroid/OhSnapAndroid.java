package com.ohsnapandroid;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.Log.ILogOutput;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.RawImage;
import com.android.ddmlib.TimeoutException;

public class OhSnapAndroid implements ClipboardOwner {

	private BufferedImage image;

	public void doScreenshot(boolean landscape) {
		Log.setLogOutput(new ILogOutput() {
			public void printAndPromptLog(LogLevel logLevel, String tag,
					String message) {
				System.err.println(logLevel.getStringValue() + ":" + tag + ":"
						+ message);
			}

			public void printLog(LogLevel logLevel, String tag, String message) {
				System.err.println(logLevel.getStringValue() + ":" + tag + ":"
						+ message);
			}
		});

		// init the lib
		// [try to] ensure ADB is running
		String adbLocation = System
				.getProperty("com.android.screenshot.bindir"); //$NON-NLS-1$
		if (adbLocation != null && adbLocation.length() != 0) {
			adbLocation += File.separator + "adb"; //$NON-NLS-1$
		} else {
			adbLocation = new File("adb").getAbsolutePath(); //$NON-NLS-1$
		}
		
		System.out.println("adb path: " + adbLocation);

		AndroidDebugBridge bridge = AndroidDebugBridge.createBridge(
				adbLocation, false /* forceNewBridge */);

		// we can't just ask for the device list right away, as the internal
		// thread getting
		// them from ADB may not be done getting the first list.
		// Since we don't really want getDevices() to be blocking, we wait here
		// manually.
		int count = 0;
		while (bridge.hasInitialDeviceList() == false) {
			try {
				Thread.sleep(100);
				count++;
			} catch (InterruptedException e) {
				// pass
			}

			// let's not wait > 10 sec.
			if (count > 100) {
				System.err.println("Timeout getting device list!");
				return;
			}
		}

		// now get the devices
		IDevice[] devices = bridge.getDevices();

		IDevice target = null;

		if (devices.length > 1) {
			reportError("Error: more than one device connected!");
			return;
		} else if (devices.length == 0) {
			reportError("Could not find a device! Make sure the device is connected to USB and 'USB Debugging' is turned on in Settings -> Applications -> Development.");
			return;
		}

		target = devices[0];

		if (target != null) {
			try {
				System.out.println("Taking screenshot from: "
						+ target.getSerialNumber());
				image = getDeviceImage(target, landscape);
				if (image != null) {
					for (int i = 0; i < rotations; i++) {
						image = rotateCw(image);
					}

					showImage(image);
				}

				System.out.println("Success.");
			} catch (Exception e) {
				reportError("Couldn't take screenshot" + e.getMessage());
			}
		} else {
			reportError("Could not find matching device/emulator.");
		}

	}

	private void reportError(String message) {
		JOptionPane.showMessageDialog(frame, message);
	}

	public void saveImage() {
		FileDialog fileDialog = new FileDialog(frame, "Save Screenshot PNG",
				FileDialog.SAVE);
		fileDialog.setFilenameFilter(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith(".png");
			}
		});
		fileDialog.setVisible(true);
		String filepath = fileDialog.getFile();

		if (filepath == null)
			return;

		if (!filepath.toLowerCase().endsWith(".png")) {
			filepath += ".png";
		}

		try {
			if (!ImageIO.write(image, "png", new File(
					fileDialog.getDirectory(), filepath))) {
				reportError("Couldn't write image file");
			}
		} catch (IOException e) {
			reportError("Couldn't write image file: " + e.getMessage());
		}
	}

	protected JFrame frame;
	protected ImageIcon imageIcon;
	protected JLabel imageLabel;
	protected int rotations;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		AndroidDebugBridge.init(false /* debugger support */);

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				AndroidDebugBridge.terminate();
			}
		});

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				new OhSnapAndroid();
			}
		});
	}

	@SuppressWarnings("serial")
	public OhSnapAndroid() {
		frame = new JFrame("Oh Snap Android - Screenshot Utility");
		frame.setLayout(new BorderLayout());
		JPanel toolbar = new JPanel(new FlowLayout());
		frame.add(toolbar, BorderLayout.NORTH);

		toolbar.add(new JButton(new AbstractAction("Take Snapshot") {
			@Override
			public void actionPerformed(ActionEvent e) {
				doScreenshot(false);
			}
		}));

		toolbar.add(new JButton(new AbstractAction("Rotate 90") {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (image != null) {
					rotations += 1;
					if (rotations > 3)
						rotations = 0;

					image = rotateCw(image);
					showImage(image);
				}
			}
		}));

		toolbar.add(new JButton(new AbstractAction("Save Image...") {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (image == null)
					doScreenshot(false);

				if (image != null)
					saveImage();
			}
		}));

		toolbar.add(new JButton(new AbstractAction("Copy to Clipboard") {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (image == null)
					doScreenshot(false);

				if (image != null) {
					Clipboard c = Toolkit.getDefaultToolkit()
							.getSystemClipboard();

					c.setContents(new TransferableImage(image),
							OhSnapAndroid.this);
				}
			}
		}));

		imageIcon = new ImageIcon();
		imageLabel = new JLabel(imageIcon);
		frame.add(imageLabel, BorderLayout.CENTER);

		frame.pack();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
	}

	protected void showImage(BufferedImage image) {
		imageIcon.setImage(image);
		imageLabel.setPreferredSize(new Dimension(image.getWidth(), image
				.getHeight()));
		imageLabel.invalidate();

		frame.pack();
		frame.validate();
		frame.repaint();
	}

	private static BufferedImage getDeviceImage(IDevice device,
			boolean landscape) throws IOException, TimeoutException,
			AdbCommandRejectedException {
		RawImage rawImage;

		rawImage = device.getScreenshot();

		// device/adb not available?
		if (rawImage == null)
			return null;

		if (landscape) {
			rawImage = rawImage.getRotated();
		}

		// convert raw data to an Image
		BufferedImage image = new BufferedImage(rawImage.width,
				rawImage.height, BufferedImage.TYPE_INT_ARGB);

		int index = 0;
		int IndexInc = rawImage.bpp >> 3;
		for (int y = 0; y < rawImage.height; y++) {
			for (int x = 0; x < rawImage.width; x++) {
				int value = rawImage.getARGB(index);
				index += IndexInc;
				image.setRGB(x, y, value);
			}
		}

		return image;
	}

	public static BufferedImage rotateCw(BufferedImage img) {
		int width = img.getWidth();
		int height = img.getHeight();
		BufferedImage newImage = new BufferedImage(height, width, img.getType());

		for (int i = 0; i < width; i++)
			for (int j = 0; j < height; j++)
				newImage.setRGB(height - 1 - j, i, img.getRGB(i, j));

		return newImage;
	}

	@Override
	public void lostOwnership(Clipboard clipboard, Transferable contents) {
		// really don't care
	}
}
