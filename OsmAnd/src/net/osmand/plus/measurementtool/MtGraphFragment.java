package net.osmand.plus.measurementtool;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.SettingsBaseActivity;
import net.osmand.plus.helpers.GpxUiHelper;
import net.osmand.plus.mapcontextmenu.other.HorizontalSelectionAdapter;
import net.osmand.plus.render.MapRenderRepositories;
import net.osmand.render.RenderingRuleSearchRequest;
import net.osmand.render.RenderingRulesStorage;
import net.osmand.router.RouteSegmentResult;
import net.osmand.router.RouteStatisticsHelper;
import net.osmand.util.Algorithms;

import static net.osmand.router.RouteStatisticsHelper.RouteStatistics;
import static net.osmand.GPXUtilities.GPXTrackAnalysis;
import static net.osmand.GPXUtilities.GPXFile;
import static net.osmand.plus.mapcontextmenu.other.HorizontalSelectionAdapter.HorizontalSelectionItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MtGraphFragment extends Fragment
		implements MeasurementToolFragment.OnUpdateAdditionalInfoListener {

	public static final String TAG = MtGraphFragment.class.getName();

	private View commonGraphContainer;
	private View customGraphContainer;
	private LineChart commonGraphChart;
	private HorizontalBarChart customGraphChart;

	private boolean nightMode;
	private MeasurementEditingContext editingCtx;
	private GraphType currentGraphType;
	private Map<GraphType, Object> graphData = new HashMap<>();

	private enum GraphType {
		OVERVIEW(R.string.shared_string_overview, false),
		ALTITUDE(R.string.altitude, false),
		SPEED(R.string.map_widget_speed, false),
		SURFACE(R.string.routeInfo_surface_name, true),
		ROAD_TYPE(R.string.routeInfo_roadClass_name, true),
		STEEPNESS(R.string.routeInfo_steepness_name, true),
		SMOOTHNESS(R.string.routeInfo_smoothness_name, true);

		GraphType(int titleId, boolean isCustomType) {
			this.titleId = titleId;
			this.isCustomType = isCustomType;
		}

		int titleId;
		boolean isCustomType;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
	                         @Nullable ViewGroup container,
	                         @Nullable Bundle savedInstanceState) {

		final MapActivity mapActivity = (MapActivity) getActivity();
		final MeasurementToolFragment mtf = (MeasurementToolFragment) getParentFragment();
		if (mapActivity == null || mtf == null) {
			return null;
		}
		editingCtx = mtf.getEditingContext();
		OsmandApplication app = mapActivity.getMyApplication();
		int activeColorId = nightMode ? R.color.active_color_primary_dark : R.color.active_color_primary_light;

		nightMode = app.getDaynightHelper().isNightModeForMapControls();
		View view = UiUtilities.getInflater(app, nightMode).inflate(
				R.layout.fragment_measurement_tool_graph, container, false);
		commonGraphContainer = view.findViewById(R.id.common_graphs_container);
		customGraphContainer = view.findViewById(R.id.custom_graphs_container);
		commonGraphChart = (LineChart) view.findViewById(R.id.line_chart);
		customGraphChart = (HorizontalBarChart) view.findViewById(R.id.horizontal_chart);
		updateGraphData();

		RecyclerView rvGraphTypes = view.findViewById(R.id.graph_types_recycler_view);
		final HorizontalSelectionAdapter horizontalSelectionAdapter = new HorizontalSelectionAdapter(app, nightMode);
		final ArrayList<HorizontalSelectionItem> items = new ArrayList<>();
		for (GraphType type : GraphType.values()) {
			String title = getString(type.titleId);
			HorizontalSelectionItem item = new HorizontalSelectionItem(title, type);
			items.add(item);
			if (type.isCustomType) {
				item.setTitleColorId(activeColorId);
			}
			boolean graphDataAvailable = graphData.get(type) != null;
			item.setEnabled(graphDataAvailable);
		}
		horizontalSelectionAdapter.setItems(items);
		horizontalSelectionAdapter.setSelectedItem(items.get(0));
		horizontalSelectionAdapter.setListener(new HorizontalSelectionAdapter.HorizontalSelectionAdapterListener() {
			@Override
			public void onItemSelected(HorizontalSelectionAdapter.HorizontalSelectionItem item) {
				horizontalSelectionAdapter.setItems(items);
				horizontalSelectionAdapter.setSelectedItem(item);
				GraphType chosenGraphType = (GraphType) item.getObject();
				if (chosenGraphType != null && !chosenGraphType.equals(currentGraphType)) {
					setupVisibleGraphType(chosenGraphType);
				}
			}
		});
		rvGraphTypes.setAdapter(horizontalSelectionAdapter);
		rvGraphTypes.setLayoutManager(new LinearLayoutManager(mapActivity, RecyclerView.HORIZONTAL, false));
		horizontalSelectionAdapter.notifyDataSetChanged();

		setupVisibleGraphType(GraphType.OVERVIEW);

		return view;
	}

	@Override
	public void onUpdateAdditionalInfo() {
		updateGraphData();
		updateGraphView(currentGraphType);
	}

	private void setupVisibleGraphType(GraphType graphType) {
		currentGraphType = graphType;
		updateGraphView(graphType);
	}

	private void updateGraphView(GraphType graphType) {
		if (graphType.isCustomType) {
			customGraphChart.clear();
			commonGraphContainer.setVisibility(View.GONE);
			customGraphContainer.setVisibility(View.VISIBLE);
			showCustomGraphView(graphType);
		} else {
			commonGraphChart.clear();
			commonGraphContainer.setVisibility(View.VISIBLE);
			customGraphContainer.setVisibility(View.GONE);
			showCommonGraphView(graphType);
		}
	}

	private void showCommonGraphView(GraphType graphType) {
		LineData data = (LineData) graphData.get(graphType);
		if (data == null) {
			return;
		}
		GpxUiHelper.setupGPXChart(commonGraphChart, 4, 24f, 16f, !nightMode, true);
//		GpxUiHelper.setupGPXChart(app, chart, 4);
		commonGraphChart.setData(data);
		updateChart(commonGraphChart);
	}

	private void showCustomGraphView(GraphType graphType) {
		BarData data = (BarData) graphData.get(graphType);
		if (data == null) {
			return;
		}
		OsmandApplication app = getMapActivity().getMyApplication();
		GpxUiHelper.setupHorizontalGPXChart(app, customGraphChart, 5, 9, 24, true, nightMode);
		customGraphChart.setExtraRightOffset(16);
		customGraphChart.setExtraLeftOffset(16);

		customGraphChart.setData(data);
		customGraphChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
			@Override
			public void onValueSelected(Entry e, Highlight h) {
//				List<RouteSegmentAttribute> elems = routeStatistics.elements;
//				int i = h.getStackIndex();
//				if (i >= 0 && elems.size() > i) {
//					selectedPropertyName = elems.get(i).getPropertyName();
//					if (showLegend) {
//						updateLegend(routeStatistics);
//					}
//				}
			}

			@Override
			public void onNothingSelected() {
//				selectedPropertyName = null;
//				if (showLegend) {
//					updateLegend(routeStatistics);
//				}
			}
		});
//		LinearLayout container = (LinearLayout) view.findViewById(R.id.route_items);
//		container.removeAllViews();
//		if (showLegend) {
//			attachLegend(container, routeStatistics);
//		}
//		final ImageView iconViewCollapse = (ImageView) view.findViewById(R.id.up_down_icon);
//		iconViewCollapse.setImageDrawable(getCollapseIcon(!showLegend));
//		view.findViewById(R.id.info_type_details_button).setOnClickListener(new View.OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				showLegend = !showLegend;
//				updateContent();
//				setLayoutNeeded();
//			}
//		});

	}

	private void updateGraphData() {
		// TODO
		List<GraphType> commonGraphs = new ArrayList<>();
		List<GraphType> customGraphs = new ArrayList<>();
		for (GraphType type : GraphType.values()) {
			if (type.isCustomType) {
				customGraphs.add(type);
			} else {
				commonGraphs.add(type);
			}
		}

		OsmandApplication app = getMyApplication();
		GPXTrackAnalysis analysis = createGpxTrackAnalysis();
		List<RouteStatistics> routeStatistics = calculateRouteStatistics(editingCtx.getAllRouteSegments());

		// update common graph data
		for (GraphType type : commonGraphs) {
			List<ILineDataSet> dataSets = getDataSets(type, commonGraphChart, analysis);
			if (!Algorithms.isEmpty(dataSets)) {
				graphData.put(type, new LineData(dataSets));
			} else {
				graphData.put(type, null);
			}
		}

		// update custom graph data
		for (GraphType type : customGraphs) {
			RouteStatistics statistic = getStatisticForGraphType(routeStatistics, type);
			if (statistic != null && !Algorithms.isEmpty(statistic.elements)) {
				BarData data = GpxUiHelper.buildStatisticChart(app, customGraphChart, statistic, analysis, true, nightMode);
				graphData.put(type, data);
			} else {
				graphData.put(type, null);
			}
		}
	}

	private RouteStatistics getStatisticForGraphType(List<RouteStatistics> routeStatistics, GraphType graphType) {
		if (routeStatistics != null) {
			for (RouteStatistics statistic : routeStatistics) {
				int graphTypeId = graphType.titleId;
				int statisticId = SettingsBaseActivity.getStringRouteInfoPropertyValueId(statistic.name);
				if (graphTypeId == statisticId) {
					return statistic;
				}
			}
		}
		return null;
	}

	private List<ILineDataSet> getDataSets(GraphType graphType, LineChart chart, GPXTrackAnalysis analysis) {
		List<ILineDataSet> dataSets = new ArrayList<>();
		if (chart != null && analysis != null) {
			OsmandApplication app = getMyApplication();
//			GpxDataItem gpxDataItem = getGpxDataItem();
//			boolean calcWithoutGaps = gpxItem.isGeneralTrack() && gpxDataItem != null && !gpxDataItem.isJoinSegments();
			switch (graphType) {
				case OVERVIEW: {
					GpxUiHelper.OrderedLineDataSet speedDataSet = null;
					GpxUiHelper.OrderedLineDataSet elevationDataSet = null;
					if (analysis.hasSpeedData) {
						speedDataSet = GpxUiHelper.createGPXSpeedDataSet(app, chart,
								analysis, GpxUiHelper.GPXDataSetAxisType.DISTANCE, true, true, false);//calcWithoutGaps);
					}
					if (analysis.hasElevationData) {
						elevationDataSet = GpxUiHelper.createGPXElevationDataSet(app, chart,
								analysis, GpxUiHelper.GPXDataSetAxisType.DISTANCE, false, true, false);//calcWithoutGaps);
					}
					if (speedDataSet != null) {
						dataSets.add(speedDataSet);
						if (elevationDataSet != null) {
							dataSets.add(elevationDataSet.getPriority() < speedDataSet.getPriority()
									? 1 : 0, elevationDataSet);
						}
					} else if (elevationDataSet != null) {
						dataSets.add(elevationDataSet);
					}
					break;
				}
				case ALTITUDE: {
					if (analysis.hasElevationData) {
						GpxUiHelper.OrderedLineDataSet elevationDataSet = GpxUiHelper.createGPXElevationDataSet(app, chart,
								analysis, GpxUiHelper.GPXDataSetAxisType.DISTANCE, false, true, false);//calcWithoutGaps);
						if (elevationDataSet != null) {
							dataSets.add(elevationDataSet);
						}
						if (analysis.hasElevationData) {
//						List<Entry> eleValues = elevationDataSet != null && !gpxItem.isGeneralTrack() ? elevationDataSet.getValues() : null;
//						GpxUiHelper.OrderedLineDataSet slopeDataSet = GpxUiHelper.createGPXSlopeDataSet(app, chart,
//								analysis, GpxUiHelper.GPXDataSetAxisType.DISTANCE, eleValues, true, true, calcWithoutGaps);
//						if (slopeDataSet != null) {
//							dataSets.add(slopeDataSet);
//						}
						}
					}
					break;
				}
				case SPEED: {
					if (analysis.hasSpeedData) {
						GpxUiHelper.OrderedLineDataSet speedDataSet = GpxUiHelper.createGPXSpeedDataSet(app, chart,
								analysis, GpxUiHelper.GPXDataSetAxisType.DISTANCE, false, true, false);//calcWithoutGaps);
						if (speedDataSet != null) {
							dataSets.add(speedDataSet);
						}
					}
					break;
				}
			}
		}
		return dataSets;
	}

	void updateChart(LineChart chart) {
//		if (chart != null && !chart.isEmpty()) {
//			if (gpxItem.chartMatrix != null) {
//				chart.getViewPortHandler().refresh(new Matrix(gpxItem.chartMatrix), chart, true);
//			}
//			if (gpxItem.chartHighlightPos != -1) {
//				chart.highlightValue(gpxItem.chartHighlightPos, 0);
//			} else {
		chart.highlightValue(null);
//			}
//		}
	}

	private GPXTrackAnalysis createGpxTrackAnalysis() {  // TODO
		GPXFile gpx;
		if (editingCtx.getGpxData() != null) {
			gpx = editingCtx.getGpxData().getGpxFile();
		} else {
			gpx = editingCtx.exportRouteAsGpx("my_test_gpx");
		}
		return gpx != null ? gpx.getAnalysis(0) : null;
	}

	private List<RouteStatistics> calculateRouteStatistics(List<RouteSegmentResult> route) {
		OsmandApplication app = getMyApplication();
		if (route != null && app != null) {
			RenderingRulesStorage currentRenderer = app.getRendererRegistry().getCurrentSelectedRenderer();
			RenderingRulesStorage defaultRender = app.getRendererRegistry().defaultRender();
			MapRenderRepositories maps = app.getResourceManager().getRenderer();
			RenderingRuleSearchRequest currentSearchRequest =
					maps.getSearchRequestWithAppliedCustomRules(currentRenderer, nightMode);
			RenderingRuleSearchRequest defaultSearchRequest =
					maps.getSearchRequestWithAppliedCustomRules(defaultRender, nightMode);
			return RouteStatisticsHelper.calculateRouteStatistic(route, currentRenderer,
					defaultRender, currentSearchRequest, defaultSearchRequest);
		}
		return null;
	}

	private OsmandApplication getMyApplication() {
		return getMapActivity().getMyApplication();
	}

	private MapActivity getMapActivity() {
		return (MapActivity) getActivity();
	}
}
