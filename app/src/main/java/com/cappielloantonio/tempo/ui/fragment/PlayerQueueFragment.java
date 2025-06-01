package com.cappielloantonio.tempo.ui.fragment;

import android.content.ComponentName;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.databinding.InnerFragmentPlayerQueueBinding;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.ui.adapter.PlayerSongQueueAdapter;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.viewmodel.PlayerBottomSheetViewModel;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;

@UnstableApi
public class PlayerQueueFragment extends Fragment implements ClickCallback {
    private static final String TAG = "PlayerQueueFragment";

    private InnerFragmentPlayerQueueBinding bind;

    private PlayerBottomSheetViewModel playerBottomSheetViewModel;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private PlayerSongQueueAdapter playerSongQueueAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        bind = InnerFragmentPlayerQueueBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        playerBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(PlayerBottomSheetViewModel.class);

        initQueueRecyclerView();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        initializeBrowser();
        bindMediaController();
    }

    @Override
    public void onResume() {
        super.onResume();
        setMediaBrowserListenableFuture();
        updateNowPlayingItem();
    }

    @Override
    public void onStop() {
        releaseBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void initializeBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    private void bindMediaController() {
        mediaBrowserListenableFuture.addListener(() -> {
            try {
                MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                initShuffleButton(mediaBrowser);
                initCleanButton(mediaBrowser);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void setMediaBrowserListenableFuture() {
        playerSongQueueAdapter.setMediaBrowserListenableFuture(mediaBrowserListenableFuture);
    }

    private void initQueueRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        bind.playerQueueRecyclerView.setLayoutManager(layoutManager);
        bind.playerQueueRecyclerView.setHasFixedSize(true);
        
        // 设置recyclerView的动画
        bind.playerQueueRecyclerView.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator() {
            @Override
            public boolean animateChange(@NonNull RecyclerView.ViewHolder oldHolder, @NonNull RecyclerView.ViewHolder newHolder, int fromX, int fromY, int toX, int toY) {
                return super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY);
            }

            @Override
            public boolean animateMove(RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
                return super.animateMove(holder, fromX, fromY, toX, toY);
            }
        });

        playerSongQueueAdapter = new PlayerSongQueueAdapter(this);
        bind.playerQueueRecyclerView.setAdapter(playerSongQueueAdapter);
        
        // 添加列表项装饰器以显示Material Design 3样式的分割线
        bind.playerQueueRecyclerView.addItemDecoration(new androidx.recyclerview.widget.DividerItemDecoration(requireContext(), layoutManager.getOrientation()));
        
        playerBottomSheetViewModel.getQueueSong().observe(getViewLifecycleOwner(), queue -> {
            if (queue != null) {
                playerSongQueueAdapter.setItems(queue.stream().map(item -> (Child) item).collect(Collectors.toList()));
            }
        });

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) {
            int originalPosition = -1;
            int fromPosition = -1;
            int toPosition = -1;
            float elevation = 0f;

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (originalPosition == -1) {
                    originalPosition = viewHolder.getBindingAdapterPosition();
                    // 保存原始高度
                    elevation = ViewCompat.getElevation(viewHolder.itemView);
                    // 增加选中项的高度以创建Material Design 3风格的抬升效果
                    ViewCompat.setElevation(viewHolder.itemView, elevation + 8f);
                    // 添加触觉反馈
                    viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                }

                fromPosition = viewHolder.getBindingAdapterPosition();
                toPosition = target.getBindingAdapterPosition();

                Collections.swap(playerSongQueueAdapter.getItems(), fromPosition, toPosition);
                Objects.requireNonNull(recyclerView.getAdapter()).notifyItemMoved(fromPosition, toPosition);

                return true;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);

                if (originalPosition != -1 && fromPosition != -1 && toPosition != -1) {
                    // 恢复原始高度
                    ViewCompat.setElevation(viewHolder.itemView, elevation);
                    // 执行重新排序
                    MediaManager.swap(mediaBrowserListenableFuture, playerSongQueueAdapter.getItems(), originalPosition, toPosition);
                    // 添加触觉反馈
                    viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                }

                originalPosition = -1;
                fromPosition = -1;
                toPosition = -1;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // 添加触觉反馈
                viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                MediaManager.remove(mediaBrowserListenableFuture, playerSongQueueAdapter.getItems(), viewHolder.getBindingAdapterPosition());
                viewHolder.getBindingAdapter().notifyDataSetChanged();
            }
        }).attachToRecyclerView(bind.playerQueueRecyclerView);
    }

    private void initShuffleButton(MediaBrowser mediaBrowser) {
        bind.playerShuffleQueueFab.setOnClickListener(view -> {
            int startPosition = mediaBrowser.getCurrentMediaItemIndex() + 1;
            int endPosition = playerSongQueueAdapter.getItems().size() - 1;

            if (startPosition < endPosition) {
                // 添加按钮动画
                view.animate()
                    .rotation(360f)
                    .setDuration(500)
                    .withEndAction(() -> {
                        view.setRotation(0f);
                        // 执行洗牌操作
                        performShuffle(mediaBrowser, startPosition, endPosition);
                    })
                    .start();
                
                // 触觉反馈
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            }
        });
    }

    private void performShuffle(MediaBrowser mediaBrowser, int startPosition, int endPosition) {
        ArrayList<Integer> pool = new ArrayList<>();
        for (int i = startPosition; i <= endPosition; i++) {
            pool.add(i);
        }

        while (pool.size() >= 2) {
            int fromPosition = (int) (Math.random() * (pool.size()));
            int positionA = pool.get(fromPosition);
            pool.remove(fromPosition);

            int toPosition = (int) (Math.random() * (pool.size()));
            int positionB = pool.get(toPosition);
            pool.remove(toPosition);

            Collections.swap(playerSongQueueAdapter.getItems(), positionA, positionB);
            // 添加随机延迟以创建级联动画效果
            bind.playerQueueRecyclerView.postDelayed(() -> {
                bind.playerQueueRecyclerView.getAdapter().notifyItemMoved(positionA, positionB);
            }, (long)(Math.random() * 150));
        }

        MediaManager.shuffle(mediaBrowserListenableFuture, playerSongQueueAdapter.getItems(), startPosition, endPosition);
    }

    private void initCleanButton(MediaBrowser mediaBrowser) {
        bind.playerCleanQueueButton.setOnClickListener(view -> {
            int startPosition = mediaBrowser.getCurrentMediaItemIndex() + 1;
            int endPosition = playerSongQueueAdapter.getItems().size();

            if (startPosition < endPosition) {
                // 添加按钮动画效果
                view.animate()
                    .alpha(0.5f)
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction(() -> {
                        view.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start();
                        
                        // 执行清除操作
                        MediaManager.removeRange(mediaBrowserListenableFuture, playerSongQueueAdapter.getItems(), startPosition, endPosition);
                        bind.playerQueueRecyclerView.getAdapter().notifyItemRangeRemoved(startPosition, endPosition);
                    })
                    .start();

                // 添加触觉反馈
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            }
        });
    }

    private void updateNowPlayingItem() {
        playerSongQueueAdapter.notifyDataSetChanged();
    }

    @Override
    public void onMediaClick(Bundle bundle) {
        MediaManager.startQueue(mediaBrowserListenableFuture, bundle.getParcelableArrayList(Constants.TRACKS_OBJECT), bundle.getInt(Constants.ITEM_POSITION));
    }
}