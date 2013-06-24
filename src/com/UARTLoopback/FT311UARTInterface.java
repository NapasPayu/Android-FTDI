//User must modify the below package with their package name
package com.UARTLoopback; 
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;
import com.UARTLoopback.Globals;


/******************************FT311 GPIO interface class******************************************/
public class FT311UARTInterface extends Activity
{

	private static final String ACTION_USB_PERMISSION =    "com.UARTLoopback.USB_PERMISSION";
	public UsbManager usbmanager;
	public UsbAccessory usbaccessory;
	public PendingIntent mPermissionIntent;
	public ParcelFileDescriptor filedescriptor = null;
	public FileInputStream inputstream = null;
	public FileOutputStream outputstream = null;
	public boolean mPermissionRequestPending = false;
	public read_thread readThread;
        public Thread writeThread;
        public write_runnable writeRun;

	final int  maxnumbytes = 262144; // 65536;
        // private int readBufferSize  = maxnumbytes;
        final private int readBufferSize = 32768;
        // private int readBufferSize = 32768;
        
	private byte [] read_usb_data; 
	private byte []	write_usb_data;


	// private byte  [] readBuffer; /*circular buffer*/
        // readBuffer = new byte [maxnumbytes];
        private byte  []readBuffer = new byte [maxnumbytes];

	private int readcount;
	private int totalBytes;
	private int writeIndex;
	private int readIndex;
	private byte status;
        // private write_thread writing_test;




	public boolean datareceived = false;
	public boolean READ_ENABLE = false;
	public boolean accessory_attached = false;

	public Context global_context;

        public static String ManufacturerString = "mManufacturer=ACCES I/O Products, Inc.";
	public static String ModelString1 = "mModel=ANDROID-232";
	public static String ModelString2 = "mModel=Android Accessory FT311D";
	public static String VersionString = "mVersion=1.0";

	public SharedPreferences intsharePrefSettings;

	/*constructor*/
	public FT311UARTInterface(Context context, SharedPreferences sharePrefSettings){
		super();
		global_context = context;
		intsharePrefSettings = sharePrefSettings;
		/*shall we start a thread here or what*/

                // read_usb_data = new byte[readBufferSize];
                // read_usb_data  = new byte[maxnumbytes];
		read_usb_data = new byte[1024]; 
		write_usb_data = new byte[256];


		/*128(make it 256, but looks like bytes should be enough)*/
		readBuffer = new byte [maxnumbytes];


		readIndex = 0;
		writeIndex = 0;
		/***********************USB handling******************************************/

		usbmanager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
		// Log.d("LED", "usbmanager" +usbmanager);
		mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		context.registerReceiver(mUsbReceiver, filter);

		inputstream = null;
		outputstream = null;
	}


	public void SetConfig(int baud, byte dataBits, byte stopBits,byte parity, byte flowControl) {

		/*prepare the baud rate buffer*/
		write_usb_data[0] = (byte)baud;
		write_usb_data[1] = (byte)(baud >> 8);
		write_usb_data[2] = (byte)(baud >> 16);
		write_usb_data[3] = (byte)(baud >> 24);

		/*data bits*/
		write_usb_data[4] = dataBits;
		/*stop bits*/
		write_usb_data[5] = stopBits;
		/*parity*/
		write_usb_data[6] = parity;
		/*flow control*/
		write_usb_data[7] = flowControl;

		/*send the UART configuration packet*/
		SendPacket((int)8);
	}


	/*write data*/
	public byte SendData(int numBytes, byte[] buffer) {
		status = 0x00; /*success by default*/
		/*
		 * if num bytes are more than maximum limit
		 */
		if (numBytes < 1) {
			/*return the status with the error in the command*/
			return status;
		}
		/*check for maximum limit*/
		if (numBytes > 256) {
			numBytes = 256;
		}

		/*prepare the packet to be sent*/
		for(int count = 0;count<numBytes;count++) {	
			write_usb_data[count] = buffer[count];
		}

		if (numBytes != 64) {
			SendPacket(numBytes);
		} else {
			byte temp = write_usb_data[63];
			SendPacket(63);
			write_usb_data[0] = temp;
			SendPacket(1);
		}

		return status;
	}

        private class write_runnable implements Runnable { 
                public int counter = 0;
                int numbytes = 256;
                boolean running = true;
                public write_runnable( int numseconds) { 
                }
                public write_runnable() { 
                }
                public void run() { 
                    Log.i(com.UARTLoopback.Globals.LOGSTR,"Starting write test");
                    while ( !Thread.currentThread().isInterrupted() && running ) { 
                        try {
                            for( int i = 0; i < 256; i ++ , counter ++) { 
                                write_usb_data[i] = (byte)i;
                            }
                            Thread.sleep(1);
                            SendPacket(numbytes);
                            Log.i(com.UARTLoopback.Globals.LOGSTR,"After writing bytes");

                        } catch (InterruptedException e) {
                            System.out.println("Exception" +  e);
                            running = false;
                        }
                    }
                    Log.i(com.UARTLoopback.Globals.LOGSTR,"Thread is done...");
                }
        }

        /* write data loop test */
        public byte WriteTest(int numseconds )
        {
            byte status = 0x0;

            // writingThread = new Thread( new write_runnable() );
            // write_runnable runnable    = new write_runnable();
            writeRun = new write_runnable();            
            writeThread = new Thread( writeRun  );

            Log.i(com.UARTLoopback.Globals.LOGSTR,"Long write test");

            // Fire off the long running thread
            writeThread.start();

            Toast.makeText(global_context, "Write Test Started", Toast.LENGTH_SHORT).show();
            return status;
        }

        public int getWriteTestNumBytes() {
            return writeRun.counter;
        }

        public void EndWriteTest() 
        {
            Log.i(com.UARTLoopback.Globals.LOGSTR,"Signaling for the thread to end");
            try {
                writeThread.interrupt();
                Log.i(com.UARTLoopback.Globals.LOGSTR,"Waiting for the thread to terminate");
                writeThread.join();
            } catch ( InterruptedException e ) {
                System.out.println("Error: " + e );
            }

            Log.i(com.UARTLoopback.Globals.LOGSTR,"Doing something else here");
            Toast.makeText(global_context, "Write Test Completed", Toast.LENGTH_SHORT).show();
        }
        

	/*read data*/
	public byte ReadData(int numBytes,byte[] buffer, int [] actualNumBytes) 
        {
		status = 0x00; /*success by default*/

		/*should be at least one byte to read*/
		if ((numBytes < 1) || (totalBytes == 0)){
			actualNumBytes[0] = 0;
			status = 0x01;
			return status;
		}

		/*check for max limit*/
		if (numBytes > totalBytes)
			numBytes = totalBytes;

		/*update the number of bytes available*/
		totalBytes -= numBytes;

		actualNumBytes[0] = numBytes;	

		/*copy to the user buffer*/	
		for(int count = 0; count<numBytes;count++) {
			buffer[count] = readBuffer[readIndex];
			readIndex++;
			/*shouldnt read more than what is there in the buffer,
			 * 	so no need to check the overflow
			 */
			readIndex %= maxnumbytes;
		}
		return status;
	}

	/*method to send on USB*/
	private void SendPacket(int numBytes)
	{	
		try {
			if (outputstream != null){
				outputstream.write(write_usb_data, 0,numBytes);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*resume accessory*/
	public int ResumeAccessory()
	{
		// Intent intent = getIntent();
		if (inputstream != null && outputstream != null) {
			return 1;
		}

		UsbAccessory[] accessories = usbmanager.getAccessoryList();
		if (accessories != null) {
			Toast.makeText(global_context, "Accessory Attached", Toast.LENGTH_SHORT).show();
		}
		else {
			// return 2 for accessory detached case
			//Log.e(">>@@","ResumeAccessory RETURN 2 (accessories == null)");
			accessory_attached = false;
			return 2;
		}

		UsbAccessory accessory = (accessories == null ? null : accessories[0]);
		if (accessory != null) {
			if ( -1 == accessory.toString().indexOf(ManufacturerString)) {
				Toast.makeText(global_context, "Manufacturer is not matched!", Toast.LENGTH_SHORT).show();
				return 1;
			}

			if ( -1 == accessory.toString().indexOf(ModelString1) && -1 == accessory.toString().indexOf(ModelString2)) {
				Toast.makeText(global_context, "Model is not matched!", Toast.LENGTH_SHORT).show();
				return 1;
			}

			if ( -1 == accessory.toString().indexOf(VersionString)) {
				Toast.makeText(global_context, "Version is not matched!", Toast.LENGTH_SHORT).show();
				return 1;
			}

			Toast.makeText(global_context, "Manufacturer, Model & Version are matched!", Toast.LENGTH_SHORT).show();
			accessory_attached = true;

			if (usbmanager.hasPermission(accessory)) {
				OpenAccessory(accessory);
			} else {
				synchronized (mUsbReceiver) {
					if (!mPermissionRequestPending) {
						Toast.makeText(global_context, "Request USB Permission", Toast.LENGTH_SHORT).show();
						usbmanager.requestPermission(accessory,
								mPermissionIntent);
						mPermissionRequestPending = true;
					}
				}
			}
		} else {}

		return 0;
	}

	/*destroy accessory*/
	public void DestroyAccessory(boolean bConfiged) {

		if (true == bConfiged) {
			READ_ENABLE = false;  // set false condition for handler_thread to exit waiting data loop
			write_usb_data[0] = 0;  // send dummy data for instream.read going
			SendPacket(1);
		} else {
			SetConfig(9600,(byte)1,(byte)8,(byte)0,(byte)0);  // send default setting data for config
			try{Thread.sleep(10);}
			catch(Exception e){}

			READ_ENABLE = false;  // set false condition for handler_thread to exit waiting data loop
			write_usb_data[0] = 0;  // send dummy data for instream.read going
			SendPacket(1);
			if (true == accessory_attached) {
                            saveDefaultPreference();
			}
		}

		try{
                    Thread.sleep(10);
                } catch(Exception e){
                }			
		CloseAccessory();
	}

	/*********************helper routines*************************************************/		

	public void OpenAccessory(UsbAccessory accessory)
	{
		filedescriptor = usbmanager.openAccessory(accessory);
		if (filedescriptor != null){
			usbaccessory = accessory;

			FileDescriptor fd = filedescriptor.getFileDescriptor();

			inputstream = new FileInputStream(fd);
			outputstream = new FileOutputStream(fd);
			/*check if any of them are null*/
			if (inputstream == null || outputstream==null){
				return;
			}

			if (READ_ENABLE == false){
				READ_ENABLE = true;
				readThread = new read_thread(inputstream);
				readThread.start();
			}
		}
	}

	private void CloseAccessory()
	{
		try{
			if (filedescriptor != null)
				filedescriptor.close();

		}catch (IOException e){}

		try {
			if (inputstream != null)
				inputstream.close();
		} catch(IOException e){}

		try {
			if (outputstream != null)
				outputstream.close();

		}catch(IOException e){}
		/*FIXME, add the notfication also to close the application*/

		filedescriptor = null;
		inputstream = null;
		outputstream = null;

		System.exit(0);
	}

	protected void saveDetachPreference() {
		if (intsharePrefSettings != null)
		{
			intsharePrefSettings.edit()
			.putString("configed", "FALSE")
			.commit();
		}
	}

	protected void saveDefaultPreference() {
		if (intsharePrefSettings != null)
		{
			intsharePrefSettings.edit().putString("configed", "TRUE").commit();
			intsharePrefSettings.edit().putInt("baudRate", 9600).commit();
			intsharePrefSettings.edit().putInt("stopBit", 1).commit();
			intsharePrefSettings.edit().putInt("dataBit", 8).commit();
			intsharePrefSettings.edit().putInt("parity", 0).commit();			
			intsharePrefSettings.edit().putInt("flowControl", 0).commit();
		}
	}

	/***********USB broadcast receiver*******************************************/
	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
					if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
					{
						Toast.makeText(global_context, "Allow USB Permission", Toast.LENGTH_SHORT).show();
						OpenAccessory(accessory);
					} 
					else 
					{
						Toast.makeText(global_context, "Deny USB Permission", Toast.LENGTH_SHORT).show();
						Log.d("LED", "permission denied for accessory "+ accessory);

					}
					mPermissionRequestPending = false;
				}
			} 
			else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) 
			{
				saveDetachPreference();
				DestroyAccessory(true);
				//CloseAccessory();
			}else
			{
				Log.d("LED", "....");
			}
		}	
	};

	/*usb input data handler*/
	private class read_thread  extends Thread {
		FileInputStream instream;
                int message_limiter = 0;
                
		read_thread(FileInputStream stream ){
			instream = stream;
			this.setPriority(Thread.MAX_PRIORITY);
		}

		public void run() {		
			while(READ_ENABLE == true) {
				while(totalBytes > (maxnumbytes - readBufferSize)) {
					try {
						Thread.sleep(50);
                                                Log.e(com.UARTLoopback.Globals.LOGSTR,"Shouldn't be here !");
					}
					catch (InterruptedException e) {e.printStackTrace();}
				}
				try {
                                    if (instream != null) {	

                                        //                                        readcount = instream.read(read_usb_data,0,readBufferSize);
                                        readcount = instream.read(read_usb_data,0,1024);

                                        Log.d( com.UARTLoopback.Globals.LOGSTR ,"Read count:" + readcount);

                                        if (readcount > 0) {
                                            for(int count = 0;count<readcount;count++) {					    			
                                                readBuffer[writeIndex] = read_usb_data[count];
                                                writeIndex++;
                                                writeIndex %= maxnumbytes;
                                            }
                                            
                                            if (writeIndex >= readIndex)
                                                totalBytes = writeIndex-readIndex;
                                            else
                                                totalBytes = (maxnumbytes-readIndex)+writeIndex;
                                            
                                            Log.e(com.UARTLoopback.Globals.LOGSTR ,"totalBytes:"+totalBytes + " writeIndex:" + writeIndex);
                                        }
                                    }
				}
				catch (IOException e){e.printStackTrace();}
			}
		}
	}
}