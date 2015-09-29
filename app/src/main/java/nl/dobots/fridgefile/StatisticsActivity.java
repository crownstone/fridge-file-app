package nl.dobots.fridgefile;

import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.achartengine.tools.ZoomEvent;
import org.achartengine.tools.ZoomListener;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import nl.dobots.bluenet.ble.base.structs.BleAlertState;

public class StatisticsActivity extends AppCompatActivity implements ZoomListener {

	private static final String TAG = StatisticsActivity.class.getCanonicalName();

	private RelativeLayout _layGraph;

	private TemperatureDbAdapter _temperatureDb;

	private HashMap<String, Integer> deviceSeriesMap = new HashMap<>();
	private XYMultipleSeriesRenderer _multipleSeriesRenderer;
	private XYMultipleSeriesDataset _dataSet;
	private GraphicalView _graphView;
	private long _minTemp;
	private long _maxTemp;
	private long _minTime;
	private long _maxTime;
	private ImageButton _btnZoomIn;
	private ImageButton _btnZoomOut;
	private ImageButton _btnZoomReset;
	private RelativeLayout _layStatistics;

	private int _zoomLevel = 0;
	private boolean _offline;
	private long _liveMinTime;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		_temperatureDb = FridgeFile.getInstance().getTemperatureDb();

		initUI();

		showGraph();
	}

	private void initUI() {
		setContentView(R.layout.activity_statistics);

		_layStatistics = (RelativeLayout) findViewById(R.id.layStatistics);

		_layGraph = (RelativeLayout) findViewById(R.id.graph);

		_btnZoomIn = (ImageButton) findViewById(R.id.zoomIn);
		_btnZoomIn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				_graphView.zoomIn();
				_zoomLevel++;
			}
		});
		_btnZoomOut = (ImageButton) findViewById(R.id.zoomOut);
		_btnZoomOut.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				_graphView.zoomOut();
				_zoomLevel--;
			}
		});
		_btnZoomReset = (ImageButton) findViewById(R.id.zoomReset);
		_btnZoomReset.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				_graphView.zoomReset();
				_zoomLevel = 0;
			}
		});
	}

	@Override
	protected void onStart() {
		super.onStart();
		FridgeFile.getInstance().getFridgeService().addListener(_fridgeListener);
	}

	@Override
	protected void onStop() {
		super.onStop();
		FridgeFile.getInstance().getFridgeService().removeListener(_fridgeListener);
	}

	private void showGraph() {

		Date now = new Date();

		StoredBleDeviceList deviceList = FridgeFile.getInstance().getStoredDeviceList();
		ArrayList<Pair<String, ArrayList<Pair<Long, Long>>>> deviceDataList = new ArrayList<>();
		for (StoredBleDevice device : deviceList.toList()) {
			String address = device.getAddress();
			ArrayList<Pair<Long, Long>> deviceData = getData(address, now);
			deviceDataList.add(new Pair<>(address, deviceData));
		}

		createTemperatureGraph(deviceDataList);

	}

	private ArrayList<Pair<Long, Long>> getData(String address, Date date) {

		ArrayList<Pair<Long, Long>> data = new ArrayList<>();

		// fetch entries for given date
		Cursor cursor = _temperatureDb.fetchEntriesForDate(address, date);

		// as long as there are entries
		while (!cursor.isAfterLast()) {

			// get count
			long temperature = cursor.getLong(cursor.getColumnIndexOrThrow(TemperatureDbAdapter.KEY_TEMPERATURE));
			// get timestamp
			long time = cursor.getLong(cursor.getColumnIndexOrThrow(TemperatureDbAdapter.KEY_DATETIME));

			// add to list
			data.add(new Pair<>(time, temperature));

			// go to next
			cursor.moveToNext();
		}
		cursor.close();

		return data;
	}

	private PointStyle[] listOfPointStyles = new PointStyle[] { PointStyle.CIRCLE, PointStyle.DIAMOND,
		PointStyle.POINT, PointStyle.SQUARE, PointStyle.TRIANGLE, PointStyle.X };

	private int[] listOfSeriesColors = new int[] { 0xFF00BFFF, Color.GREEN, Color.RED, Color.YELLOW,
		Color.MAGENTA, Color.CYAN, Color.BLACK };

	private void createTemperatureGraph(ArrayList<Pair<String, ArrayList<Pair<Long, Long>>>> data) {

		// get graph renderer
		_multipleSeriesRenderer = getRenderer();

		_dataSet = new XYMultipleSeriesDataset();

		int currentPointStyle = 0;
		int currentSeriesColor = 0;
		int currentSeires = 0;

		_minTemp = Integer.MAX_VALUE;
		_maxTemp = Integer.MIN_VALUE;

		_minTime = Long.MAX_VALUE;
		_maxTime = Long.MIN_VALUE;

		StoredBleDeviceList storedDeviceList = FridgeFile.getInstance().getStoredDeviceList();

		for (Pair<String, ArrayList<Pair<Long, Long>>> device : data) {
			// make sure data is not empty
			if (device.second.isEmpty()) {
				Toast.makeText(this, "No Data found", Toast.LENGTH_LONG).show();
				return;
			}

			String seriesTitle = storedDeviceList.get(device.first).getName();

			// create time series (series with x = timestamp, y = temperature)
			TimeSeries series = new TimeSeries(seriesTitle);

			for (Pair<Long, Long> entry : device.second) {
				series.add(new Date(entry.first), entry.second);
				if (entry.second < _minTemp) {
					_minTemp = entry.second;
				}
				if (entry.second > _maxTemp) {
					_maxTemp = entry.second;
				}
				if (entry.first < _minTime) {
					_minTime = entry.first;
				}
				if (entry.first > _maxTime) {
					_maxTime = entry.first;
				}
			}

			_dataSet.addSeries(series);

			// create new renderer for the new series
			XYSeriesRenderer renderer = new XYSeriesRenderer();
			_multipleSeriesRenderer.addSeriesRenderer(renderer);

			renderer.setPointStyle(listOfPointStyles[currentPointStyle]);
			renderer.setColor(listOfSeriesColors[currentSeriesColor]);
			renderer.setFillPoints(false);
			renderer.setDisplayChartValues(true);
			renderer.setDisplayChartValuesDistance(200);
			renderer.setChartValuesTextSize(30f);
			renderer.setShowLegendItem(true);

			currentPointStyle = (currentPointStyle + 1) % listOfPointStyles.length;
			currentSeriesColor = (currentSeriesColor + 1) % listOfSeriesColors.length;

			deviceSeriesMap.put(device.first, currentSeires);
			currentSeires++;
		}

		long diff = _maxTemp - _minTemp;
		_minTemp = Math.min(0, (long)(_minTemp - diff * 0.2));
		_maxTemp = (long)(_maxTemp + diff * 0.2);

		_liveMinTime = new Date().getTime() - 30 * 60 * 1000;

		addAlertSeries();

		_multipleSeriesRenderer.setInitialRange(new double[] {_liveMinTime, _maxTime, _minTemp, _maxTemp});

		// create graph
		_graphView = ChartFactory.getTimeChartView(this, _dataSet, _multipleSeriesRenderer, null);
		_graphView.addZoomListener(this, false, true);

		// add to screen
		_layGraph.addView(_graphView);
	}

	private void addAlertSeries() {
		TimeSeries alertSeries = new TimeSeries("Alerts");
		long alertTime = _maxTime - 5000 * 60;
		alertSeries.add(alertTime, Long.MAX_VALUE);
		alertSeries.add(alertTime, Long.MIN_VALUE);

		_dataSet.addSeries(alertSeries);

		// create new renderer for the new series
		XYSeriesRenderer renderer = new XYSeriesRenderer();
		_multipleSeriesRenderer.addSeriesRenderer(renderer);

		renderer.setPointStyle(PointStyle.POINT);
		renderer.setColor(Color.RED);
		renderer.setFillPoints(false);
		renderer.setDisplayChartValues(false);
//		renderer.setDisplayChartValuesDistance(1);
//		renderer.setChartValuesTextSize(30f);
		renderer.setShowLegendItem(false);

	}

	/**
	 * Create graph renderer
	 *
	 * @return renderer object
	 */
	public XYMultipleSeriesRenderer getRenderer() {
		XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();

		// set minimum for y axis to 0
		renderer.setYAxisMin(-20);
		renderer.setYAxisMax(+100);

		// scrolling enabled
//		renderer.setPanEnabled(true, false);
		renderer.setPanEnabled(true, true);
		// limits for scrolling (minx, maxx, miny, maxy)
		// zoom buttons (in, out, 1:1)
		renderer.setZoomButtonsVisible(true);
		// enable zoom
//		renderer.setZoomEnabled(true, false);
		renderer.setZoomEnabled(true, true);

		// set labels text size
		renderer.setLabelsTextSize(30f);

		renderer.setYLabelsAlign(Paint.Align.RIGHT);
		renderer.setAxisTitleTextSize(30f);
		renderer.setYTitle("Temperature [°C]");

		// hide legend
//		renderer.setShowLegend(false);
		renderer.setShowLegend(true);
		renderer.setLegendTextSize(30f);
		renderer.setLegendHeight(130);

		// set margins
//		renderer.setMargins(new int[] {20, 30, 15, 0});
		renderer.setMargins(new int[] {30, 70, 50, 0});

//		renderer.setApplyBackgroundColor(true);
//		renderer.setBackgroundColor(Color.WHITE);
//		renderer.setMarginsColor(Color.WHITE);

		renderer.setMarginsColor(Color.argb(0x00, 0x01, 0x01, 0x01));
		// todo: need to get background colour of activity, transparent is not good enough
//		renderer.setMarginsColor(((ColorDrawable) _layGraph.getBackground()).getColor());

		renderer.setXAxisMin(new Date().getTime() - 30 * 60 * 1000);

		renderer.setZoomButtonsVisible(false);
		renderer.setExternalZoomEnabled(true);

//		XYSeriesRenderer r = new XYSeriesRenderer();

		// set color
//		r.setColor(Color.GREEN);

		// set fill below line
//		r.setFillBelowLine(true);

//		renderer.addSeriesRenderer(r);
		return renderer;
	}

	private void setOffline(boolean isOffline) {
		_offline = isOffline;
	}

	//////////////////////////////////////////
	// Communication with the BleFridgeService
	//////////////////////////////////////////
	final BleFridgeServiceListener _fridgeListener = new BleFridgeServiceListener() {
		@Override
		public void onTemperature(StoredBleDevice device, int temperature) {
			if (_offline) {
				return;
			}

			// add new point
			int seriesIdx = deviceSeriesMap.get(device.getAddress());
			TimeSeries series = (TimeSeries)_dataSet.getSeriesAt(seriesIdx);
			series.add(new Date(), temperature);

			// update y-axis range
			if (temperature > _maxTemp) {
				_maxTemp = (long)(temperature + (temperature - _minTemp) * 0.2);
			}
			if (temperature < _minTemp) {
				_minTemp = Math.min(0, (long)(temperature - (_maxTemp - temperature) * 0.2));
			}

			// update x-axis range
			_maxTime = new Date().getTime();
			_liveMinTime = _maxTime - 30 * 60 * 1000;

			// update range
			if (_zoomLevel == 0) {
				_multipleSeriesRenderer.setInitialRange(new double[]{_liveMinTime, _maxTime, _minTemp, _maxTemp});
				_multipleSeriesRenderer.setRange(new double[]{_liveMinTime, _maxTime, _minTemp, _maxTemp});
			}

			// redraw
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					_graphView.repaint();
				}
			});
		}

		@Override
		public void onAlert(StoredBleDevice device, BleAlertState oldAlertState, BleAlertState newAlertState) {

//			// checking alert levels
//			if (newAlertState.isTemperatureLowActive() && !oldAlertState.isTemperatureLowActive()) {
//				String notificationSmall = String.format("Temperature Low Alert (%d °C)", device.getCurrentTemperature());
//				String notificationBig = notificationSmall += String.format(" for Device %s [%s]",
//						device.getName(), device.getAddress());
//				createAlertNotification(notificationSmall, notificationBig);
//				_alertDb.createEntry(new Date(), notificationSmall);
//			}
//			if (newAlertState.isTemperatureHighActive() && !oldAlertState.isTemperatureHighActive()) {
//				String notificationSmall = String.format("Temperature High Alert (%d °C)",
//						device.getCurrentTemperature());
//				String notificationBig = notificationSmall += String.format(" for Device %s [%s]",
//						device.getName(), device.getAddress());
//				createAlertNotification(notificationSmall, notificationBig);
//				_alertDb.createEntry(new Date(), notificationSmall);
//			}
		}
	};

	@Override
	public void zoomApplied(ZoomEvent zoomEvent) {
		_zoomLevel = 100;
	}

	@Override
	public void zoomReset() {
		_zoomLevel = 0;
	}

	private void showToday() {
		_multipleSeriesRenderer.setInitialRange(new double[] {_minTime, _maxTime, _minTemp, _maxTemp});
		_multipleSeriesRenderer.setRange(new double[] {_minTime, _maxTime, _minTemp, _maxTemp});
		// redraw
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				_graphView.repaint();
			}
		});
	}

	private void showLive() {
		_liveMinTime = new Date().getTime() - 30 * 60 * 1000;
		_multipleSeriesRenderer.setRange(new double[] {_liveMinTime, new Date().getTime(), _minTemp, _maxTemp});
		_multipleSeriesRenderer.setInitialRange(new double[] {_liveMinTime, new Date().getTime(), _minTemp, _maxTemp});
		// redraw
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				_graphView.repaint();
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_statistics, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		switch (item.getItemId()) {
			case R.id.action_today:
				setOffline(true);
				showToday();
				return true;
			case R.id.action_live:
				setOffline(false);
				showLive();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

}
