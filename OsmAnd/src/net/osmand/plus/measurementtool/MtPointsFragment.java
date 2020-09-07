package net.osmand.plus.measurementtool;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.measurementtool.adapter.MeasurementToolAdapter;
import net.osmand.plus.measurementtool.command.ReorderPointCommand;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.views.controls.ReorderItemTouchHelperCallback;

public class MtPointsFragment extends Fragment
		implements MeasurementToolFragment.OnUpdateAdditionalInfoListener {

	public static final String TAG = MtPointsFragment.class.getName();

	private boolean nightMode;
	private MeasurementToolAdapter adapter;
	private MeasurementEditingContext editingCtx;
	private RecyclerView pointsRv;

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
		nightMode = mapActivity.getMyApplication().getDaynightHelper().isNightModeForMapControls();
		View view = UiUtilities.getInflater(getContext(), nightMode)
				.inflate(R.layout.fragment_measurement_tool_points_list, container, false);

		editingCtx = mtf.getEditingContext();
		final GpxData gpxData = editingCtx.getGpxData();
		adapter = new MeasurementToolAdapter(mapActivity, editingCtx.getPoints(),
				gpxData != null ? gpxData.getActionType() : null);
		pointsRv = view.findViewById(R.id.measure_points_recycler_view);
		ItemTouchHelper touchHelper = new ItemTouchHelper(new ReorderItemTouchHelperCallback(adapter));
		touchHelper.attachToRecyclerView(pointsRv);
		adapter.setAdapterListener(createMeasurementAdapterListener(touchHelper));
		pointsRv.setLayoutManager(new LinearLayoutManager(getContext()));
		pointsRv.setAdapter(adapter);

		return view;
	}

	private MeasurementToolAdapter.MeasurementAdapterListener createMeasurementAdapterListener(final ItemTouchHelper touchHelper) {
		return new MeasurementToolAdapter.MeasurementAdapterListener() {

			final MapActivity mapActivity = (MapActivity) getActivity();
			final MeasurementToolFragment mtf = (MeasurementToolFragment) getParentFragment();
			final MeasurementToolLayer measurementLayer = mtf != null ? mtf.getMeasurementLayer() : null;
			private int fromPosition;
			private int toPosition;

			@Override
			public void onRemoveClick(int position) {
				if (mtf != null) {
					mtf.removePoint(measurementLayer, position);
				}
			}

			@Override
			public void onItemClick(int position) {
				if (mapActivity != null && measurementLayer != null && mtf != null) {
					if (AndroidUiHelper.isOrientationPortrait(mapActivity)) {
						mtf.setMapPosition(OsmandSettings.MIDDLE_TOP_CONSTANT);
					}
					measurementLayer.moveMapToPoint(position);
					measurementLayer.selectPoint(position);
					mtf.collapseAdditionalInfo(); //TODO
				}
			}

			@Override
			public void onDragStarted(RecyclerView.ViewHolder holder) {
				fromPosition = holder.getAdapterPosition();
				touchHelper.startDrag(holder);
			}

			@Override
			public void onDragEnded(RecyclerView.ViewHolder holder) {
				if (mapActivity != null && measurementLayer != null && mtf != null) {
					toPosition = holder.getAdapterPosition();
					if (toPosition >= 0 && fromPosition >= 0 && toPosition != fromPosition) {
						editingCtx.getCommandManager().execute(new ReorderPointCommand(measurementLayer, fromPosition, toPosition));
						adapter.notifyDataSetChanged();
						mtf.updateUndoRedoButton(false, false);
						mtf.updateDistancePointsText();
						mapActivity.refreshMap();
					}
				}
			}
		};
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		adapter.setAdapterListener(null);
	}

	@Override
	public void onUpdateAdditionalInfo() {
		adapter.notifyDataSetChanged();
	}
}
