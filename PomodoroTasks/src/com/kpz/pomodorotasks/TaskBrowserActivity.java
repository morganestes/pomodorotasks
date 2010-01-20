package com.kpz.pomodorotasks;

import android.R.drawable;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Vibrator;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.SimpleCursorAdapter.ViewBinder;

import com.kpz.pomodorotasks.TaskDatabaseAdapter.StatusType;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TaskBrowserActivity extends ListActivity {
    
	private static final String LOG_TAG = "PomodoroTasks";

	private static final int NOTIFICATION_ID = R.layout.tasks_list;
	
	private static final int ACTIVITY_EDIT = 1;
	private static final int ACTIVITY_SET_OPTIONS = 2;
	
	private static final int MAIN_MENU_DELETE_ALL_ID = Menu.FIRST;
	private static final int MAIN_MENU_DELETED_COMPLETED_ID = MAIN_MENU_DELETE_ALL_ID + 1;
	private static final int MAIN_MENU_OPTIONS_ID = MAIN_MENU_DELETE_ALL_ID + 2;
	private static final int MAIN_MENU_QUIT_ID = MAIN_MENU_DELETE_ALL_ID + 3;
	
	private static final int ONE_SEC = 1000;
	private static final int FIVE_MIN_IN_SEC = 300;
	
	private static final String TAG = "PomodoroTasks";	
    
	private ListView taskList;
    private TaskDatabaseAdapter mTasksDatabaseHelper;
	private TextView mTaskDescription;
	private ProgressBar mProgressBar;
	private TextView mTimeLeft;
	private MyCount counter;
	private ImageButton taskControlButton;
	private Vibrator vibrator;
	private Cursor taskListCursor;
	private LinearLayout runTaskPanel;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        initView();
    }
    
    @Override
    protected void onDestroy() {

    	if (mConnection != null){
    		unbindService(mConnection);    		
    	}
    	
    	stopService(new Intent(TaskBrowserActivity.this, 
                NotifyingService.class));
    	
    	super.onDestroy();
    }
    
	private void initView() {

		setContentView(NOTIFICATION_ID);
        
        vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
        
        initDatabaseHelper();        
        initTasksList();
        initAddTaskInput();
        initAndHideRunTaskPanel();   
	}

	private void initAndHideRunTaskPanel() {
		runTaskPanel = (LinearLayout)findViewById(R.id.runTaskPanel);
		runTaskPanel.setVisibility(View.GONE);		
		taskControlButton = (ImageButton) findViewById(R.id.control_icon);
		hideButton = (ImageButton) findViewById(R.id.hide_panel_button);
		hideButton.setVisibility(View.VISIBLE);
		hideButton.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				runTaskPanel.setVisibility(View.GONE);
			}
		});
		
    	mTaskDescription = (TextView) findViewById(R.id.task_description);
    	mTimeLeft = (TextView) findViewById(R.id.time_left);
    	mProgressBar = (ProgressBar) findViewById(R.id.progress_horizontal);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// doing nothing to the view when screen orientation changes
	}
	
	private void initRunTaskPanel(final String taskDescription) {
		
    	taskControlButton.setOnClickListener(new View.OnClickListener() {

    	    public void onClick(View view) {

    	    	if (counter != null && taskControlButton.getTag(R.string.TASK_CONTROL_BUTTON_STATE_TYPE).equals(R.string.TO_STOP_STATE)){
    	    		resetTaskRun(taskControlButton);
    	    	} else {
    	    		beginTimeTask(taskDescription);
    	    	}
    	    }
    	});
	}

	private void showAndStartRunTaskPanel(String taskDescription) {

		initRunTaskPanel(taskDescription);
		showRunTaskPanel(taskDescription);
		resetTaskRun(taskControlButton);
		beginTimeTask(taskDescription);
	}
	
	private void showRunTaskPanel(String taskDescription) {
		
		runTaskPanel.setVisibility(View.VISIBLE);
		mTaskDescription.setText(taskDescription);
	}
	
	private void beginTimeTask(String taskDescription){
		
		int totalTime = mTasksDatabaseHelper.fetchTaskDurationSetting() * 60;
		beginTask(taskDescription, totalTime, true);
	}
	
	private void beginBreakTask(){
		
		int totalTime = FIVE_MIN_IN_SEC;
		beginTask("Take a Break", totalTime, false);
	}
	
	private void beginTask(final String taskDescription, int totalTime, boolean isTimeTask) {
	
		mTaskDescription.setText(taskDescription);
		
		mProgressBar.setMax(totalTime);
		counter = new MyCount(totalTime * ONE_SEC, ONE_SEC, beepHandler, isTimeTask);
		//counter = new ProgressThread(handler);
		counter.start();
		
		hideButton.setVisibility(View.INVISIBLE);
		taskControlButton.setImageResource(R.drawable.stop);
		taskControlButton.setTag(R.string.TASK_CONTROL_BUTTON_STATE_TYPE, R.string.TO_STOP_STATE);

	    mConnection = new ServiceConnection() {

			public void onServiceConnected(ComponentName className, IBinder service) {
	            // This is called when the connection with the service has been
	            // established, giving us the service object we can use to
	            // interact with the service.  Because we have bound to a explicit
	            // service that we know is running in our own process, we can
	            // cast its IBinder to a concrete class and directly access it.
	            mBoundService = ((NotifyingService.LocalBinder)service).getService();
	            mBoundService.notifyTimeStarted(taskDescription);
	        }

	        public void onServiceDisconnected(ComponentName className) {
	            // This is called when the connection with the service has been
	            // unexpectedly disconnected -- that is, its process crashed.
	            // Because it is running in our same process, we should never
	            // see this happen.
	            mBoundService = null;
	        }
	    };
		
		bindService(new Intent(TaskBrowserActivity.this, 
				NotifyingService.class), 
				mConnection, 
				Context.BIND_AUTO_CREATE);
		
	}
	
	private NotifyingService mBoundService;
	
    private void refreshTaskPanel() {
    	
    	String text = mTaskDescription.getText().toString();
    	
    	if (text == null || text.equals("")){
    		return;
    	}
    	
    	boolean exists = false;
    	final int count = getListAdapter().getCount();
        for (int i = count - 1; i >= 0; i--) {

        	Cursor cursor = (Cursor)getListAdapter().getItem(i);
        	String taskDescription = cursor.getString(cursor.getColumnIndex(TaskDatabaseAdapter.KEY_DESCRIPTION));
            if(taskDescription.equals(text)){
				exists = true;
            }
        }    
        
        if (!exists){
        	mTaskDescription.setText("");
        }
	}

	private void resetTaskRun(final ImageButton taskControlButton) {
		if (counter != null){
    		counter.cancel();
    	}
		
		resetProgressControl(taskControlButton);
		
		if(mBoundService != null){
			mBoundService.cancelTaskNotification();
		}
	}

	private void resetProgressControl(final ImageButton taskControlButton) {
		resetTimeElapsed();
		mProgressBar.setProgress(0);
//        taskControlButton.setImageResource(drawable.ic_media_play);
        taskControlButton.setImageResource(R.drawable.play);
        taskControlButton.setTag(R.string.TASK_CONTROL_BUTTON_STATE_TYPE, R.string.TO_PLAY_STATE);
        adjustDimensionsToDefault(taskControlButton);
        hideButton.setVisibility(View.VISIBLE);
	}

	private void resetTimeElapsed() {
		
		mTimeLeft.setText(mTasksDatabaseHelper.fetchTaskDurationSetting() + ":00");
	}

	private boolean isRunTaskPanelInitialized() {
		return runTaskPanel.getVisibility() == View.VISIBLE;
	}
	
	private boolean isTaskRunning() {
		return mProgressBar.getProgress() != 0;
	}

	private void adjustDimensionsToDefault(final ImageButton taskControlButton) {
		Button leftButton = (Button) findViewById(R.id.left_text_button);
		taskControlButton.getLayoutParams().height = leftButton.getHeight();
		taskControlButton.getLayoutParams().width = leftButton.getWidth();
	}
	
	private void initTasksList() {
		initTasksListViewContainer();
        populateTasksList();
	}
	
    private void populateTasksList() {
    	
    	Cursor tasksCursor = mTasksDatabaseHelper.fetchAll();
        startManagingCursor(tasksCursor);
        
        // Create an array to specify the fields we want to display in the list (only Description)
        String[] from = new String[]{TaskDatabaseAdapter.KEY_DESCRIPTION, TaskDatabaseAdapter.KEY_STATUS};
        
        // and an array of the fields we want to bind those fields to (in this case just task_description)
        int[] to = new int[]{R.id.task_description, R.id.taskRow};
        
        SimpleCursorAdapter taskListCursorAdapter = new SimpleCursorAdapter(getApplication(), R.layout.tasks_row, tasksCursor, from, to);
        taskListCursorAdapter.setViewBinder(new ViewBinder() {
			
			public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
				
				if (view.getId() == R.id.taskRow){

					TextView textView = (TextView)view.findViewById(R.id.task_description);
					
					String statusText = cursor.getString(columnIndex);
					if (StatusType.OPEN.getDescription().equals(statusText)){
						textView.getPaint().setStrikeThruText(false);
					} else if (StatusType.COMPLETED.getDescription().equals(statusText)){
						textView.getPaint().setStrikeThruText(true);
					}
					
					return true;
				}
				
				return false;
			}
		});
        
        setListAdapter(taskListCursorAdapter);
        taskListCursor = taskListCursorAdapter.getCursor();
    }

	private void initTasksListViewContainer() {
		taskList = getListView();
        ((TouchInterceptor) taskList).setDropListener(mDropListener);
        ((TouchInterceptor) taskList).setCheckOffListener(mCheckOffListener);
        taskList.setCacheColorHint(0);
	}
	
	public void checkOffTask(int which, View targetView) {

		TextView textView = (TextView)targetView.findViewById(R.id.task_description);
    	textView.getPaint().setStrikeThruText(true);
    	
    	Cursor cursor = (Cursor)getListAdapter().getItem(which);
		int rowId = Integer.parseInt(cursor.getString(cursor.getColumnIndex(TaskDatabaseAdapter.KEY_ROWID)));
		mTasksDatabaseHelper.updateStatus(rowId, true);
		refreshTasksList();
	}
	
    private boolean refreshTasksList() {

    	return taskListCursor.requery();
	}

	public void uncheckOffTask(int which, View targetView) {

    	TextView textView = (TextView)targetView.findViewById(R.id.task_description);
    	textView.getPaint().setStrikeThruText(false);
    	
    	Cursor cursor = (Cursor)getListAdapter().getItem(which);
		int rowId = Integer.parseInt(cursor.getString(cursor.getColumnIndex(TaskDatabaseAdapter.KEY_ROWID)));
		mTasksDatabaseHelper.updateStatus(rowId, false);
		refreshTasksList();
	}
	
    private TouchInterceptor.CheckOffListener mCheckOffListener = new TouchInterceptor.CheckOffListener() {
    	
        public void checkOff(int which) {

        	View targetView = (View)taskList.getChildAt(which - taskList.getFirstVisiblePosition());
        	checkOffTask(which, targetView);
        }
        
		public void uncheckOff(int which) {

			View targetView = (View)taskList.getChildAt(which - taskList.getFirstVisiblePosition());
			uncheckOffTask(which, targetView);
		}
    };

	private void initDatabaseHelper() {
		mTasksDatabaseHelper = new TaskDatabaseAdapter(this);
        mTasksDatabaseHelper.open();
	}

	private void initAddTaskInput() {
		final EditText leftTextEdit = (EditText) findViewById(R.id.left_text_edit);
        Button leftButton = (Button) findViewById(R.id.left_text_button);
        leftButton.setOnClickListener(new OnClickListener() {
            
        	public void onClick(View v) {
 
        		String noteDescription = leftTextEdit.getText().toString().trim();
        		if (!noteDescription.equals("")){
        			createNewTask(noteDescription);
                    refreshTasksList();
                    resetAddTaskEntryDisplay(leftTextEdit);        			
        		}
            }

			private void resetAddTaskEntryDisplay(final EditText leftTextEdit) {
				leftTextEdit.setText("");
				leftTextEdit.requestFocus();
// to hide keyboard				
//                ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(leftTextEdit.getWindowToken(), 0); 
// to display on-screen keyboard                 
//                ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))  
//                .showSoftInput(editText, 0);  
			}

			private void createNewTask(final String noteDescription) {
            	mTasksDatabaseHelper.createTask(noteDescription);
			}
        });
	}
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        MenuItem menuItem = menu.add(0, MAIN_MENU_DELETED_COMPLETED_ID, 0, R.string.menu_delete_completed);
        menuItem.setIcon(drawable.ic_menu_agenda);
        
        menuItem = menu.add(0, MAIN_MENU_DELETE_ALL_ID, 0, R.string.menu_delete_all);
        menuItem.setIcon(drawable.ic_menu_delete);
        
        menuItem = menu.add(0, MAIN_MENU_OPTIONS_ID, 0, R.string.menu_options);
        menuItem.setIcon(drawable.ic_menu_preferences);
        
        menuItem = menu.add(0, MAIN_MENU_QUIT_ID, 0, R.string.menu_quit);
        menuItem.setIcon(drawable.ic_menu_close_clear_cancel);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch(item.getItemId()) {

        case MAIN_MENU_DELETED_COMPLETED_ID:
        	mTasksDatabaseHelper.deleteCompleted();
	        refreshTasksList();
	        refreshTaskPanel();
        	return true;
        	
        case MAIN_MENU_DELETE_ALL_ID:
        	mTasksDatabaseHelper.deleteAll();
	        refreshTasksList();
	        refreshTaskPanel();
	        return true;
        	
        case MAIN_MENU_OPTIONS_ID:
        	Intent i = new Intent(this, SettingsActivity.class);
	        startActivityForResult(i, ACTIVITY_SET_OPTIONS);
        	return true;        	
        
        case MAIN_MENU_QUIT_ID:
        	finish();
        	return true;    
        }
       
        return super.onMenuItemSelected(featureId, item);
    }

	@Override
    protected void onListItemClick(ListView l, final View v, final int position, long id) {
        
    	super.onListItemClick(l, v, position, id);

    	Cursor cursor = (Cursor)getListAdapter().getItem(new Long(position).intValue());
    	final Long rowId = new Long(cursor.getString(cursor.getColumnIndex(TaskDatabaseAdapter.KEY_ROWID)));
		
    	final String[] items = {"Start", "Edit", "Delete"};
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setItems(items, new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface dialog, int item) {
		        
		    	//Toast.makeText(getApplicationContext(), items[item], Toast.LENGTH_SHORT).show();
		        
		    	switch (item) {
				case 0:
			        TextView textView = (TextView)v.findViewById(R.id.task_description);
			        showAndStartRunTaskPanel(textView.getText().toString());
					break;
					
				case 1:
					Intent i = new Intent(TaskBrowserActivity.this, TaskEditActivity.class);
			        i.putExtra(TaskDatabaseAdapter.KEY_ROWID, rowId);
			        startActivityForResult(i, ACTIVITY_EDIT);
			        break;
			        
				case 2:
			        mTasksDatabaseHelper.delete(rowId);
			        refreshTasksList();
			        break;
			        
				default:
					break;
				}
		        
		    }
		});
		AlertDialog alert = builder.create();
		alert.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, 
                                    Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        
        switch (requestCode) {
			case ACTIVITY_SET_OPTIONS:
				
				if (isRunTaskPanelInitialized() && !isTaskRunning()){
					resetTimeElapsed();
				}
				break;
			case ACTIVITY_EDIT:
				refreshTasksList();
				break;
		}
    }
    
    private TouchInterceptor.DropListener mDropListener = new TouchInterceptor.DropListener() {

    	public void drop(int from, int to) {

    		move(from, to);
        	resetBottomMargin();
        	refreshTasksList();
        }

		private void resetBottomMargin() {
			LinearLayout.LayoutParams viewGroupParams = (LinearLayout.LayoutParams)getListView().getLayoutParams();
			if (viewGroupParams.bottomMargin != 0){
				viewGroupParams.bottomMargin = 0;
	    		getListView().setLayoutParams(viewGroupParams);				
			}
		}

		private void move(int from, int to) {
			
			Cursor cursor = (Cursor)getListAdapter().getItem(from);
        	int fromSeq = Integer.parseInt(cursor.getString(cursor.getColumnIndex(TaskDatabaseAdapter.KEY_SEQUENCE)));

        	cursor = (Cursor)getListAdapter().getItem(to);
        	int toSeq = Integer.parseInt(cursor.getString(cursor.getColumnIndex(TaskDatabaseAdapter.KEY_SEQUENCE)));
    		int toRowId = Integer.parseInt(cursor.getString(cursor.getColumnIndex(TaskDatabaseAdapter.KEY_ROWID)));
        	
        	mTasksDatabaseHelper.move(fromSeq, toRowId, toSeq);
		}
    };

    final Handler beepHandler = new Handler() {
        public void handleMessage(Message msg) {

    		MediaPlayer mp = MediaPlayer.create(getBaseContext(), R.raw.freesoundprojectdotorg_32568__erh__indian_brass_pestle);
	        mp.start();
	        
	        vibrator.vibrate(1000); 
	        
	        while(mp.isPlaying()){
	        	
	        }
	        
	    	resetProgressControl(taskControlButton);
	    	
	    	boolean isTaskTime = msg.getData().getBoolean("TASK_TIME");
	    	if(isTaskTime){
	    		
	        	final String[] items = {"Take 5 min break", "Cancel"};
	    		AlertDialog.Builder builder = new AlertDialog.Builder(TaskBrowserActivity.this);
	    		builder.setItems(items, new DialogInterface.OnClickListener() {
	    		    public void onClick(DialogInterface dialog, int item) {
	    		        
	    		    	//Toast.makeText(getApplicationContext(), items[item], Toast.LENGTH_SHORT).show();
	    		    	switch (item) {
	    				case 0:
	    					beginBreakTask();
	    					break;

	    				case 1:
	    			        break;
	    			        
	    				default:
	    					break;
	    				}
	    		        
	    		    }
	    		});
	    		AlertDialog alert = builder.create();
	    		alert.show();
	    		
	    	} else {
	    		
	    		mTaskDescription.setText("");
	    	}
        }
    };

	private ImageButton hideButton;

	private ServiceConnection mConnection;
	
    public class MyCount extends CountDownTimer{
	    
		private Handler mHandler;
		private boolean isTaskTime;

		public MyCount(long millisInFuture, long countDownInterval, Handler handler, boolean isTaskTime) {
	    	super(millisInFuture + ONE_SEC, countDownInterval);
	    	this.mHandler = handler;
	    	this.isTaskTime = isTaskTime;
		}

		@Override
	    public void onTick(long millisUntilFinished) {
	    	
	    	incrementProgress(millisUntilFinished);
	    }

		private void incrementProgress(long millisUntilFinished) {
			
			final DateFormat dateFormat = new SimpleDateFormat("mm:ss");
            String timeStr = dateFormat.format(new Date(millisUntilFinished - ONE_SEC));
            mTimeLeft.setText(timeStr);
           	mProgressBar.setProgress(new Long(millisUntilFinished / ONE_SEC).intValue());
           	
           	if (timeStr.equals("00:00")){
           		beep();
           		mBoundService.notifyTimeEnded();
    	    	endTimer();
           	}
		}

		private void beep() {
			Message msg = mHandler.obtainMessage();
			Bundle bundle = new Bundle();
			bundle.putBoolean("TASK_TIME", isTaskTime);
			msg.setData(bundle);
			mHandler.sendMessage(msg);
		}
		
		private void endTimer() {
			cancel();
		}
		@Override
		public void onFinish() {
			// do nothing
		}
    }
}