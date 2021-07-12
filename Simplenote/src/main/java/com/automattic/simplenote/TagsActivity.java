package com.automattic.simplenote;

import static com.automattic.simplenote.TagDialogFragment.DIALOG_TAG;
import static com.automattic.simplenote.utils.DisplayUtils.disableScreenshotsIfLocked;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.automattic.simplenote.models.Note;
import com.automattic.simplenote.models.Tag;
import com.automattic.simplenote.models.TagItem;
import com.automattic.simplenote.utils.DisplayUtils;
import com.automattic.simplenote.utils.DrawableUtils;
import com.automattic.simplenote.utils.HtmlCompat;
import com.automattic.simplenote.utils.TagItemAdapter;
import com.automattic.simplenote.utils.ThemeUtils;
import com.automattic.simplenote.viewmodels.TagsEvent;
import com.automattic.simplenote.viewmodels.TagsViewModel;
import com.automattic.simplenote.viewmodels.ViewModelFactory;
import com.automattic.simplenote.widgets.EmptyViewRecyclerView;
import com.automattic.simplenote.widgets.MorphSetup;
import com.simperium.client.Bucket;

import kotlin.Unit;

public class TagsActivity extends ThemedAppCompatActivity {
    private static final int REQUEST_ADD_TAG = 9000;

    private EmptyViewRecyclerView mTagsList;
    private ImageButton mButtonAdd;
    private ImageView mEmptyViewImage;
    private MenuItem mSearchMenuItem;
    private TextView mEmptyViewText;

    private TagsViewModel viewModel;
    private TagItemAdapter tagItemAdapter;
    Bucket<Tag> mTagsBucket;
    Bucket<Note> mNotesBucket;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tags);

        Simplenote application = (Simplenote) getApplication();
        mTagsBucket = application.getTagsBucket();
        mNotesBucket = application.getNotesBucket();

        ViewModelFactory viewModelFactory = new ViewModelFactory(mTagsBucket, mNotesBucket, this, null);
        ViewModelProvider viewModelProvider = new ViewModelProvider(this, viewModelFactory);
        viewModel = viewModelProvider.get(TagsViewModel.class);

        tagItemAdapter = new TagItemAdapter(
                (TagItem tagItem) -> {
                    viewModel.clickEditTag(tagItem);
                    return Unit.INSTANCE;
                },
                (TagItem tagItem) -> {
                    viewModel.clickDeleteTag(tagItem);
                    return Unit.INSTANCE;
                },
                (View view) -> {
                    viewModel.longClickDeleteTag(view);
                    return Unit.INSTANCE;
                }
        );

        setupViews();

        setObservers();

        viewModel.start();
    }

    private void setupViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        SpannableString title = new SpannableString(getString(R.string.edit_tags));

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mTagsList = findViewById(R.id.list);
        mTagsList.setAdapter(tagItemAdapter);
        mTagsList.setLayoutManager(new LinearLayoutManager(TagsActivity.this));
        View emptyView = findViewById(R.id.empty);
        mEmptyViewImage = emptyView.findViewById(R.id.image);
        mEmptyViewText = emptyView.findViewById(R.id.text);
        setLabelEmptyTagList();
        mTagsList.setEmptyView(emptyView);

        mButtonAdd = findViewById(R.id.button_add);
        mButtonAdd.setOnClickListener(v -> viewModel.clickAddTag());
        mButtonAdd.setOnLongClickListener(v -> {
            viewModel.longClickAddTag();
            return true;
        });
    }

    private void setObservers() {
        viewModel.getUiState().observe(this, uiState ->
                tagItemAdapter.submitList(uiState.getTagItems(), () -> {
                    if (uiState.getSearchUpdate()) {
                        mTagsList.scrollToPosition(0);
                        boolean isSearching = uiState.getSearchQuery() != null;
                        if (isSearching) {
                            setLabelEmptyTagListSearchResults();
                        } else {
                            setLabelEmptyTagList();
                        }
                    }
                })
        );

        // Observe different events such as clicks on add tags, edit tags and delete tags
        viewModel.getEvent().observe(this, event -> {
            if (event instanceof TagsEvent.AddTagEvent) {
                startAddTagActivity();
            } else if (event instanceof TagsEvent.LongAddTagEvent) {
                showLongAddToast();
            } else if (event instanceof TagsEvent.FinishEvent) {
                finish();
            } else if (event instanceof TagsEvent.EditTagEvent) {
                showTagDialogFragment((TagsEvent.EditTagEvent) event);
            } else if (event instanceof TagsEvent.DeleteTagEvent) {
                showDeleteDialog((TagsEvent.DeleteTagEvent) event);
            } else if (event instanceof TagsEvent.LongDeleteTagEvent) {
                showLongDeleteToast((TagsEvent.LongDeleteTagEvent) event);
            }
        });
    }

    private void showLongAddToast() {
        if (mButtonAdd.isHapticFeedbackEnabled()) {
            mButtonAdd.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }

        toast(R.string.add_tag);
    }

    private void showLongDeleteToast(TagsEvent.LongDeleteTagEvent event) {
        View v = event.getView();
        if (v.isHapticFeedbackEnabled()) {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }

        toast(R.string.delete_tag);
    }

    private void toast(@StringRes int resId) {
        Toast.makeText(TagsActivity.this, getString(resId), Toast.LENGTH_SHORT).show();
    }

    private void showDeleteDialog(TagsEvent.DeleteTagEvent event) {
        AlertDialog.Builder alert = new AlertDialog.Builder(new ContextThemeWrapper(TagsActivity.this, R.style.Dialog));
        alert.setTitle(R.string.delete_tag);
        alert.setMessage(getString(R.string.confirm_delete_tag));
        alert.setNegativeButton(R.string.no, null);
        alert.setPositiveButton(
                R.string.yes,
                (dialog, whichButton) -> viewModel.deleteTag(event.getTagItem())
        );
        alert.show();
    }

    private void showTagDialogFragment(TagsEvent.EditTagEvent event) {
        TagDialogFragment dialog = new TagDialogFragment(
                event.getTagItem().getTag(),
                mNotesBucket,
                mTagsBucket
        );
        dialog.show(getSupportFragmentManager().beginTransaction(), DIALOG_TAG);
    }

    private void startAddTagActivity() {
        Intent intent = new Intent(TagsActivity.this, AddTagActivity.class);
        intent.putExtra(MorphSetup.EXTRA_SHARED_ELEMENT_COLOR_END, ThemeUtils.getColorFromAttribute(TagsActivity.this, R.attr.drawerBackgroundColor));
        intent.putExtra(MorphSetup.EXTRA_SHARED_ELEMENT_COLOR_START, ThemeUtils.getColorFromAttribute(TagsActivity.this, R.attr.fabColor));
        ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(TagsActivity.this, mButtonAdd, "shared_button");
        startActivityForResult(intent, REQUEST_ADD_TAG, options.toBundle());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.tags_list, menu);
        DrawableUtils.tintMenuWithAttribute(TagsActivity.this, menu, R.attr.toolbarIconColor);

        mSearchMenuItem = menu.findItem(R.id.menu_search);
        SearchView searchView = (SearchView) mSearchMenuItem.getActionView();
        LinearLayout searchEditFrame = searchView.findViewById(R.id.search_edit_frame);
        ((LinearLayout.LayoutParams) searchEditFrame.getLayoutParams()).leftMargin = 0;

        // Workaround for setting the search placeholder text color.
        @SuppressWarnings("ResourceType")
        String hintHexColor = getString(R.color.text_title_disabled).replace("ff", "");
        searchView.setQueryHint(
            HtmlCompat.fromHtml(
                String.format(
                    "<font color=\"%s\">%s</font>",
                    hintHexColor,
                    getString(R.string.search_tags_hint)
                )
            )
        );

        searchView.setOnQueryTextListener(
            new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextChange(String query) {
                    if (mSearchMenuItem.isActionViewExpanded()) {
                        viewModel.search(query);
                    }

                    return true;
                }

                @Override
                public boolean onQueryTextSubmit(String queryText) {
                    return true;
                }
            }
        );

        searchView.setOnCloseListener(() -> {
            viewModel.closeSearch();
            return false;
        });

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        disableScreenshotsIfLocked(this);

        viewModel.startListeningTagChanges();
    }

    @Override
    public void onPause() {
        super.onPause();

        viewModel.stopListeningTagChanges();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == RESULT_OK && requestCode == REQUEST_ADD_TAG) {
            viewModel.updateOnResult();
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void setLabelEmptyTagList() {
        setEmptyListImage(R.drawable.ic_tag_24dp);
        setEmptyListMessage(getString(R.string.empty_tags));
    }

    private void setLabelEmptyTagListSearchResults() {
        if (DisplayUtils.isLandscape(TagsActivity.this) &&
                !DisplayUtils.isLargeScreen(TagsActivity.this)) {
            setEmptyListImage(-1);
        } else {
            setEmptyListImage(R.drawable.ic_search_24dp);
        }

        setEmptyListMessage(getString(R.string.empty_tags_search));
    }

    private void setEmptyListImage(@DrawableRes int image) {
        if (mEmptyViewImage != null) {
            if (image != -1) {
                mEmptyViewImage.setVisibility(View.VISIBLE);
                mEmptyViewImage.setImageResource(image);
            } else {
                mEmptyViewImage.setVisibility(View.GONE);
            }
        }
    }

    private void setEmptyListMessage(String message) {
        if (mEmptyViewText != null && message != null) {
            mEmptyViewText.setText(message);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            viewModel.close();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
