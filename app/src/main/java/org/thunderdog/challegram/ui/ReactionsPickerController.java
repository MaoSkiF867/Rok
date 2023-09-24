/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 31/05/2023
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.emoji.MediaStickersAdapter;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.StickersListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.EmojiLayout;
import org.thunderdog.challegram.widget.EmojiMediaLayout.EmojiLayoutRecyclerController;
import org.thunderdog.challegram.widget.EmojiMediaLayout.Headers.EmojiCategoriesRecyclerView;
import org.thunderdog.challegram.widget.EmojiMediaLayout.Headers.EmojiHeaderView;
import org.thunderdog.challegram.util.StickerSetsDataProvider;
import org.thunderdog.challegram.widget.EmojiMediaLayout.Sections.EmojiSection;
import org.thunderdog.challegram.widget.EmojiMediaLayout.Sections.EmojiSectionView;
import org.thunderdog.challegram.widget.EmojiMediaLayout.Sections.StickerSectionView;
import org.thunderdog.challegram.widget.ShadowView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.Td;

public class ReactionsPickerController extends ViewController<MessageOptionsPagerController.State>
  implements StickersListener, EmojiLayoutRecyclerController.Callback,
  StickerSmallView.StickerMovementCallback, FactorAnimator.Target {

  private MessageOptionsPagerController.State state;
  private EmojiLayoutRecyclerController reactionsController;
  private CustomRecyclerView recyclerView;
  private MediaStickersAdapter adapter;

  public ReactionsPickerController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  protected View onCreateView (Context context) {
    ArrayList<EmojiSection> emojiSections = new ArrayList<>(3);
    if (state.needShowCustomEmojiInsidePicker) {
      emojiSections.add(new EmojiSection(this, -14, R.drawable.baseline_search_24, R.drawable.baseline_search_24)/*.setFactor(1f, false)*/.setMakeFirstTransparent().setOffsetHalf(false));
    }
    emojiSections.add(new EmojiSection(this, 0, R.drawable.baseline_favorite_24, R.drawable.baseline_favorite_24)/*.setFactor(1f, false)*/.setMakeFirstTransparent());
    if (state.hasNonSelectedCustomReactions) {
      emojiSections.add(new EmojiSection(this, 1, R.drawable.baseline_access_time_24, R.drawable.baseline_watch_later_24)/*.setFactor(1f, false)*/.setMakeFirstTransparent());
    }
    bottomHeaderCell = new EmojiHeaderView(context, this, this, emojiSections, null, false);
    bottomHeaderCell.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, HeaderView.getSize(false)));
    bottomHeaderCell.setIsPremium(true, false);
    bottomHeaderCell.setSectionsOnClickListener(this::onStickerSectionClick);

    recyclerView = onCreateRecyclerView();
    recyclerView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> reactionsController.invalidateStickerObjModifiers());
    recyclerView.setItemAnimator(null);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {}

      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (!reactionsController.isNeedIgnoreScroll() && !isIgnoreMovement) {
          setCurrentStickerSectionByPosition(EmojiLayout.STICKERS_INSTALLED_CONTROLLER_ID, reactionsController.getStickerSetSection(HeaderView.getSize(true) + EmojiLayout.getHeaderPadding()), true, true);
        }
      }
    });

    genTopHeader();
    genBottomHeader();
    buildCells();
    loadStickers();
    return recyclerView;
  }

  private static final float DEFAULT_STICKER_PADDING_DP = 5.5f;

  public CustomRecyclerView onCreateRecyclerView () {
    reactionsController = new EmojiLayoutRecyclerController(context, tdlib, R.id.controller_emojiLayoutReactions);
    reactionsController.setStickerObjModifier(this::modifyStickerObj);
    adapter = new MediaStickersAdapter(this, this, false, this) {
      @Override
      public void onBindViewHolder (StickerHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        int type = getItemViewType(position);
        if (type == StickerHolder.TYPE_STICKER) {
          TGStickerObj stickerObj = getSticker(position);
          ((StickerSmallView) holder.itemView).setPadding(Screen.dp(stickerObj != null && stickerObj.isEmojiReaction() ? 0: DEFAULT_STICKER_PADDING_DP));
          ((StickerSmallView) holder.itemView).setChosen(stickerObj != null && state.chosenReactions != null && stickerObj.getReactionType() != null && state.chosenReactions.contains(TD.makeReactionKey(stickerObj.getReactionType())));
        }

        holder.itemView.setVisibility(position <= reactionsController.getSpanCount() || isFullyVisible ? View.VISIBLE: View.INVISIBLE);
      }
    };


    adapter.setLayoutParams(new MediaStickersAdapter.LayoutParams(
      (int) (HeaderView.getSize(true) + MessageOptionsPagerController.getPickerTopPadding()),
      Screen.dp(9.5f),
      Screen.dp(8f),
      Screen.dp(21 - 9.5f),
      getItemHeight()
    ));
    adapter.setRepaintingColorId(ColorId.text);

    reactionsController.setArguments(this);
    reactionsController.setAdapter(adapter);
    reactionsController.setItemWidth(9, 38);
    reactionsController.getValue();
    reactionsController.getManager();

    return (CustomRecyclerView) reactionsController.getValue();
  }

  public int measureItemsHeight () {
    return reactionsController.getItemsHeight(false);
  }

  public int getItemWidth () {
    return (recyclerView.getMeasuredWidth() - recyclerView.getPaddingLeft() - recyclerView.getPaddingRight()) / reactionsController.getSpanCount();
  }

  public int getItemHeight () {
    return Screen.dp(45);
  }

  public float getTopHeaderVisibility () {
    return topHeaderVisibility.getFloatValue();
  }

  public CustomRecyclerView getRecyclerView () {
    return recyclerView;
  }

  private ArrayList<TGStickerSetInfo> emojiPacks;
  private ArrayList<MediaStickersAdapter.StickerItem> emojiItems;


  private void buildCells () {
    emojiItems = new ArrayList<>();
    emojiPacks = new ArrayList<>();

    ArrayList<MediaStickersAdapter.StickerItem> emojiItemsCustom = new ArrayList<>();

    TdApi.AvailableReaction[] reactions = state.availableReactions;
    if (reactions != null) {
      emojiItems.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_KEYBOARD_TOP));

      TGStickerSetInfo pack = TGStickerSetInfo.fromEmojiSection(tdlib, 0, -1, reactions.length);
      pack.setStartIndex(emojiItems.size());
      pack.setIsRecent();
      emojiItems.ensureCapacity(reactions.length);
      emojiPacks.add(pack);

      for (TdApi.AvailableReaction reaction: reactions) {
        final boolean isClassicEmojiReaction = reaction.type.getConstructor() == TdApi.ReactionTypeEmoji.CONSTRUCTOR;
        TGReaction reactionObj = tdlib.getReaction(reaction.type);
        TGStickerObj stickerObj = reactionObj != null ? reactionObj.newCenterAnimationSicker(): null;
        if (stickerObj != null) {
          if (isClassicEmojiReaction) {
            if (stickerObj.getPreviewAnimation() != null) {
              stickerObj.getPreviewAnimation().setPlayOnce(true);
              stickerObj.getPreviewAnimation().setLooped(false);
            }
          }
        }
        if (isClassicEmojiReaction || state.chosenReactions.contains(TD.makeReactionKey(reaction.type))) {
          emojiItems.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, stickerObj));
        } else {
          emojiItemsCustom.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, stickerObj));
        }
      }
    }

    if (!emojiItemsCustom.isEmpty()) {
      TGStickerSetInfo pack = TGStickerSetInfo.fromEmojiSection(tdlib, 1, R.string.Recent, emojiItemsCustom.size());
      pack.setStartIndex(emojiItems.size());
      pack.setIsDefaultEmoji();

      emojiPacks.get(0).setSize(reactions.length - emojiItemsCustom.size());

      emojiPacks.add(pack);
      emojiItems.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_HEADER, pack));
      emojiItems.addAll(emojiItemsCustom);
    }

    reactionsController.setStickers(emojiPacks, emojiItems);
  }

  private void onStickerSectionClick (View v) {
    /*if (scrollState != androidx.viewpager.widget.ViewPager.SCROLL_STATE_IDLE) {
      return;
    }*/

    final int viewId = v.getId();
    if (viewId == R.id.btn_stickerSet) {
      TGStickerSetInfo info = ((StickerSectionView) v).getStickerSet();
      if (info != null) {
        int index = reactionsController.indexOfStickerSet(info);
        reactionsController.scrollToStickerSet(index, HeaderView.getSize(true), false, true);
      }
    } else if (viewId == R.id.btn_section) {
      EmojiSection section = ((EmojiSectionView) v).getSection();
      if (section.index == -14) {
        bottomHeaderView.openSearchMode(true, false);
      } else if (section.index >= 0 && section.index < emojiPacks.size()) {
        reactionsController.scrollToStickerSet(section.index == 0 ? 0 : emojiPacks.get(section.index).getStartIndex(), HeaderView.getSize(true), false, true);
      }
    }
  }

  public void scrollToDefaultPosition (int offset) {
    reactionsController.scrollToStickerSet(0, offset, -offset, false, true);
  }

  /* * */

  public void prepareToShow () {
    reactionsController.getManager();
    setIsFullyVisible(true);
  }

  private boolean loadingStickers;

  private void loadStickers () {
    if (loadingStickers || !state.needShowCustomEmojiInsidePicker) {
      return;
    }

    loadingStickers = true;
    tdlib.client().send(new TdApi.GetInstalledStickerSets(new TdApi.StickerTypeCustomEmoji()), stickerSetsHandler());
  }

  private Client.ResultHandler stickerSetsHandler () {
    return object -> {
      switch (object.getConstructor()) {
        case TdApi.StickerSets.CONSTRUCTOR: {
          TdApi.StickerSetInfo[] rawStickerSets = ((TdApi.StickerSets) object).sets;

          final ArrayList<TGStickerSetInfo> stickerSets = new ArrayList<>(rawStickerSets.length);
          final ArrayList<MediaStickersAdapter.StickerItem> items = new ArrayList<>();

          if (rawStickerSets.length > 0) {
            int startIndex = this.adapter.getItemCount();

            for (TdApi.StickerSetInfo rawInfo : rawStickerSets) {
              TGStickerSetInfo info = new TGStickerSetInfo(tdlib, rawInfo);
              if (info.getSize() == 0) {
                continue;
              }
              stickerSets.add(info);
              info.setStartIndex(startIndex);
              items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_HEADER, info));
              for (int i = 0; i < rawInfo.size; i++) {
                TGStickerObj sticker = new TGStickerObj(tdlib, i < rawInfo.covers.length ? rawInfo.covers[i] : null, null, rawInfo.stickerType);
                sticker.setStickerSetId(rawInfo.id, null);
                sticker.setDataProvider(stickerSetsDataProvider());
                items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, sticker));
              }
              startIndex += rawInfo.size + 1;
            }
          }

          runOnUiThreadOptional(() -> {
            /*if (getArguments() != null) {
              getArguments().setEmojiPacks(stickerSets);
            }*/
            setStickers(stickerSets, items);
          });

          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          UI.showError(object);
          break;
        }
      }
    };
  }

  private void setStickers (ArrayList<TGStickerSetInfo> stickerSets, ArrayList<MediaStickersAdapter.StickerItem> items) {
    this.emojiPacks.addAll(stickerSets);
    this.emojiItems.addAll(items);

    this.reactionsController.addStickers(stickerSets, items);
    this.loadingStickers = false;
    if (stickerSetsDataProvider != null) {
      this.stickerSetsDataProvider.clear();
    }
    bottomHeaderCell.setStickerSets(stickerSets);
    recyclerView.invalidateItemDecorations();
  }

  /* * */

  @Override
  public int getId () {
    return R.id.controller_reactionsPicker;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.ReactionsPickerHeader);
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  protected boolean allowMenuReuse () {
    return false;
  }

  @Override
  protected int getHeaderTextColorId () {
    return ColorId.text;
  }

  @Override
  protected int getHeaderColorId () {
    return ColorId.filling;
  }

  @Override
  protected int getHeaderIconColorId () {
    return ColorId.icon;
  }

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  @Override
  public void setArguments (MessageOptionsPagerController.State args) {
    this.state = args;
    super.setArguments(args);
  }

  @Override
  public void destroy () {
    super.destroy();
    reactionsController.destroy();
  }

  @Override
  public boolean onStickerClick (StickerSmallView view, View clickView, TGStickerObj sticker, boolean isMenuClick, TdApi.MessageSendOptions sendOptions) {
    TdApi.ReactionType reactionType = sticker.isCustomEmoji() ?
      new TdApi.ReactionTypeCustomEmoji(sticker.getCustomEmojiId()): sticker.getReactionType();

    TGReaction reaction = tdlib.getReaction(reactionType);
    if (reaction == null && sticker.isCustomEmoji() && sticker.getSticker() != null) {
      reaction = new TGReaction(tdlib, sticker.getSticker());
    }

    if (reaction != null) {
      getArgumentsStrict().onReactionClickListener.onReactionClick(clickView, reaction, false);
      return true;
    }
    return false;
  }

  @Override
  public boolean onStickerLongClick (StickerSmallView view, TGStickerObj sticker) {
    TdApi.ReactionType reactionType = sticker.isCustomEmoji() ?
      new TdApi.ReactionTypeCustomEmoji(sticker.getCustomEmojiId()): sticker.getReactionType();

    TGReaction reaction = tdlib.getReaction(reactionType);
    if (reaction == null && sticker.isCustomEmoji() && sticker.getSticker() != null) {
      reaction = new TGReaction(tdlib, sticker.getSticker());
    }

    if (reaction != null) {
      getArgumentsStrict().onReactionClickListener.onReactionClick(view, reaction, true);
    }

    return true;
  }

  @Override
  public long getStickerOutputChatId () {
    return 0;
  }

  @Override
  public void setStickerPressed (StickerSmallView view, TGStickerObj sticker, boolean isPressed) {

  }

  @Override
  public boolean canFindChildViewUnder (StickerSmallView view, int recyclerX, int recyclerY) {
    return false;
  }

  @Override
  public boolean needsLongDelay (StickerSmallView view) {
    return false;
  }

  @Override
  public int getStickersListTop () {
    return 0;
  }

  @Override
  public int getViewportHeight () {
    return 0;
  }



  /* Emoji Layout Callbacks */

  private boolean isIgnoreMovement;

  public boolean isIgnoreMovement () {
    return isIgnoreMovement;
  }

  @Override
  public void setIgnoreMovement (boolean silent) {
    isIgnoreMovement = silent;
  }

  @Override
  public void resetScrollState (boolean silent) {

  }

  @Override
  public void moveHeader (int totalDy) {

  }

  @Override
  public void setHasNewHots (int controllerId, boolean hasHots) {

  }

  @Override
  public boolean onStickerClick (int controllerId, StickerSmallView view, View clickView, TGStickerSetInfo stickerSet, TGStickerObj sticker, boolean isMenuClick, TdApi.MessageSendOptions sendOptions) {
    return false;
  }

  @Override
  public boolean canFindChildViewUnder (int controllerId, StickerSmallView view, int recyclerX, int recyclerY) {
    return true;
  }

  @Override
  public Context getContext () {
    return context;
  }

  @Override
  public boolean isUseDarkMode () {
    return false;
  }

  @Override
  public long findOutputChatId () {
    return 0;
  }

  @Override
  public void onSectionInteracted (int mediaType, boolean interactionFinished) {

  }

  @Override
  public void onSectionInteractedScroll (int mediaType, boolean moved) {

  }

  @Override
  public void setCurrentStickerSectionByPosition (int controllerId, int i, boolean isStickerSection, boolean animated) {
    bottomHeaderCell.setCurrentStickerSectionByPosition(i + (state.needShowCustomEmojiInsidePicker ? 1: 0), animated);
  }

  @Override
  public void onAddStickerSection (int controllerId, int section, TGStickerSetInfo info) {
    bottomHeaderCell.addStickerSection(section, info);
  }

  @Override
  public void onMoveStickerSection (int controllerId, int fromSection, int toSection) {
    bottomHeaderCell.moveStickerSection(fromSection, toSection);
  }

  @Override
  public void onRemoveStickerSection (int controllerId, int section) {
    bottomHeaderCell.removeStickerSection(section);
  }

  @Override
  public boolean isAnimatedEmojiOnly () {
    return false;
  }

  @Override
  public float getHeaderHideFactor () {
    return 0;
  }

  @Override
  public void hideSoftwareKeyboard () {
    if (fakeControllerForBottomHeader != null) {
      fakeControllerForBottomHeader.hideSoftwareKeyboard();
    }
    super.hideSoftwareKeyboard();
  }


  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    if (headerView != null) {
      headerView.resetColors(this, null);
    }
    if (bottomHeaderView != null) {
      bottomHeaderView.resetColors(this, null);
    }
  }



  /* Data provider */

  private StickerSetsDataProvider stickerSetsDataProvider;

  private StickerSetsDataProvider stickerSetsDataProvider() {
    if (stickerSetsDataProvider != null) {
      return stickerSetsDataProvider;
    }

    return stickerSetsDataProvider = new StickerSetsDataProvider(tdlib) {
      @Override
      protected boolean needIgnoreRequests (long stickerSetId, TGStickerObj stickerObj) {
        return reactionsController.isIgnoreRequests(stickerSetId);
      }

      @Override
      protected int getLoadingFlags (long stickerSetId, TGStickerObj stickerObj) {
        return FLAG_REGULAR;
      }

      @Override
      protected void applyStickerSet (TdApi.StickerSet stickerSet, int flags) {
        if (BitwiseUtils.hasFlag(flags, FLAG_REGULAR)) {
          reactionsController.applyStickerSet(stickerSet, this, false);
        }
      }
    };
  }


  /* Visibility control */

  private boolean isFullyVisible = false;

  public void setIsFullyVisible (boolean isFullyVisible) {
    if (this.isFullyVisible == isFullyVisible) {
      return;
    }

    this.isFullyVisible = isFullyVisible;

    int itemsCount = adapter.getItemCount();
    int start = reactionsController.getSpanCount() + 1;
    if (itemsCount > start) {
      adapter.notifyItemRangeChanged(start, itemsCount - start);
    }
  }



  /* Top Header */

  private LinearLayout topHeaderViewGroup;

  public HeaderView getTopHeaderView () {
    return headerView;
  }

  public ViewGroup getTopHeaderViewGroup () {
    return topHeaderViewGroup;
  }

  private boolean topHeaderVisibilityValue;

  public void setTopHeaderVisibility (boolean isVisible) {
    if (topHeaderVisibilityValue == isVisible) {
      return;
    }
    topHeaderVisibilityValue = isVisible;
    UI.post(() -> {
      topHeaderViewGroup.setVisibility(View.VISIBLE);
      topHeaderVisibility.setValue(isVisible, true);
    });
  }



  private void genTopHeader () {
    headerView = new HeaderView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent e) {
        super.onTouchEvent(e);
        return true;
      }
    };
    headerView.initWithSingleController(this, false);
    headerView.setBackgroundHeight(Screen.dp(56));
    headerView.getBackButton().setIsReverse(true);
    addThemeInvalidateListener(headerView);

    View lickView = new View(context) {
      @Override
      protected void dispatchDraw (Canvas canvas) {
        canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(),
          Paints.fillingPaint(ColorUtils.compositeColors(Theme.getColor(ColorId.statusBar), Theme.getColor(ColorId.headerLightBackground))));
      }
    };
    lickView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, HeaderView.getTopOffset()));
    addThemeInvalidateListener(lickView);

    topHeaderViewGroup = new LinearLayout(context);
    topHeaderViewGroup.setOrientation(LinearLayout.VERTICAL);
    topHeaderViewGroup.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, HeaderView.getSize(true) + ShadowView.simpleBottomShadowHeight()));
    topHeaderViewGroup.addView(lickView);
    topHeaderViewGroup.addView(headerView);
    topHeaderViewGroup.setVisibility(View.GONE);
    topHeaderViewGroup.setAlpha(0f);
  }



  /* Bottom Header */

  private FrameLayout bottomHeaderViewGroup;
  private FakeControllerForBottomHeader fakeControllerForBottomHeader;
  private HeaderView bottomHeaderView;
  private EmojiHeaderView bottomHeaderCell;
  private CustomRecyclerView emojiTypesRecyclerView;
  private EmojiCategoriesRecyclerView.EmojiSearchTypesAdapter emojiSearchTypesAdapter;
  private boolean ignoreSearchInputUpdates;

  public HeaderView getBottomHeaderView () {
    return bottomHeaderView;
  }

  public FrameLayout getBottomHeaderViewGroup () {
    return bottomHeaderViewGroup;
  }

  protected void onBottomHeaderEnterSearchMode () {

  }

  protected void onBottomHeaderLeaveSearchMode () {

  }

  public boolean inBottomHeaderSearchMode () {
    return fakeControllerForBottomHeader.inSearchMode();
  }

  private void genBottomHeader () {
    fakeControllerForBottomHeader = new FakeControllerForBottomHeader(context, tdlib) {
      @Override
      public View getCustomHeaderCell () {
        return bottomHeaderCell;
      }

      @Override
      protected void onEnterSearchMode () {
        super.onEnterSearchMode();
        emojiSearchTypesAdapter.setActiveIndex(-1);
        emojiTypesRecyclerView.scrollToPosition(0);
        emojiTypesRecyclerView.setVisibility(View.VISIBLE);
        emojiTypesRecyclerView.setAlpha(bottomHeaderSearchModeVisibility.getFloatValue());
        bottomHeaderSearchModeVisibility.setValue(true, true);
        searchEmojiImpl(null);
        onBottomHeaderEnterSearchMode();
      }

      @Override
      protected void onSearchInputChanged (String query) {
        super.onSearchInputChanged(query);
        if (!ignoreSearchInputUpdates) {
          emojiSearchTypesAdapter.setActiveIndex(-1);
          searchEmojiImpl(query);
        }
      }

      @Override
      protected void onLeaveSearchMode () {
        super.onLeaveSearchMode();
        bottomHeaderSearchModeVisibility.setValue(false, true);
        searchEmojiImpl(null);
      }

      @Override
      protected void onAfterLeaveSearchMode () {
        super.onAfterLeaveSearchMode();
        onBottomHeaderLeaveSearchMode();
      }
    };

    bottomHeaderView = new HeaderView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent e) {
        super.onTouchEvent(e);
        return true;
      }
    };
    bottomHeaderView.initWithSingleController(fakeControllerForBottomHeader, false);
    bottomHeaderView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM));

    emojiSearchTypesAdapter = new EmojiCategoriesRecyclerView.EmojiSearchTypesAdapter(this, this::searchEmojiSection);
    emojiTypesRecyclerView = new EmojiCategoriesRecyclerView(context);
    emojiTypesRecyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT | Gravity.BOTTOM, Screen.dp(56), 0, 0, 0));
    emojiTypesRecyclerView.setAdapter(emojiSearchTypesAdapter);
    emojiTypesRecyclerView.setAlpha(0);
    emojiTypesRecyclerView.setVisibility(View.GONE);

    bottomHeaderViewGroup = new FrameLayout(context);
    bottomHeaderViewGroup.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, HeaderView.getSize(false), Gravity.BOTTOM));
    bottomHeaderViewGroup.addView(bottomHeaderView);
    bottomHeaderViewGroup.addView(emojiTypesRecyclerView);



    tdlib.send(new TdApi.GetEmojiCategories(new TdApi.EmojiCategoryTypeDefault()), object -> {
      if (object.getConstructor() == TdApi.EmojiCategories.CONSTRUCTOR) {
        TdApi.EmojiCategories categories = (TdApi.EmojiCategories) object;
        UI.post(() -> emojiSearchTypesAdapter.setEmojiCategories(categories.categories));
      }
    });
  }

  private String lastEmojiSearchRequest;

  private void searchEmojiSection (String request) {
    ignoreSearchInputUpdates = true;
    fakeControllerForBottomHeader.clearSearchInput();
    ignoreSearchInputUpdates = false;

    searchEmojiImpl(request);
  }



  /* Search */

  private TdlibUi.EmojiStickers lastEmojiStickers;

  private void searchEmojiImpl (final String request) {
    if (StringUtils.equalsOrBothEmpty(lastEmojiSearchRequest, request)) {
      return;
    }

    lastEmojiSearchRequest = request;

    if (!StringUtils.isEmpty(request)) {
      if (lastEmojiStickers == null || !StringUtils.equalsOrBothEmpty(lastEmojiStickers.query, request)) {
        lastEmojiStickers = tdlib.ui().getEmojiStickers(new TdApi.StickerTypeCustomEmoji(), request, true, 2000, findOutputChatId());
      }
      lastEmojiStickers.getStickers((context, installedStickers, recommendedStickers, b) -> {
        if (StringUtils.equalsOrBothEmpty(lastEmojiSearchRequest, context.query)) {
          final ArrayList<TdApi.Sticker> stickers = new ArrayList<>(Arrays.asList(installedStickers));
          if (recommendedStickers != null) {
            stickers.addAll(Arrays.asList(recommendedStickers));
          }

          final ArrayList<MediaStickersAdapter.StickerItem> items = new ArrayList<>(1 + stickers.size());
          final ArrayList<TGStickerSetInfo> packs = new ArrayList<>(1);

          items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_KEYBOARD_TOP));

          TGStickerSetInfo pack = TGStickerSetInfo.fromEmojiSection(tdlib, -1, -1, stickers.size());
          pack.setStartIndex(items.size());
          pack.setIsRecent();
          packs.add(pack);

          for (TdApi.Sticker value : stickers) {
            TGStickerObj sticker = new TGStickerObj(tdlib, value, null, value.fullType);
            sticker.setReactionType(new TdApi.ReactionTypeCustomEmoji(value.id));
            items.add(new MediaStickersAdapter.StickerItem(MediaStickersAdapter.StickerHolder.TYPE_STICKER, sticker));
          }

          reactionsController.clearAllItems();
          reactionsController.setStickers(packs, items);
        }
      }, 0);
    } else {
      reactionsController.clearAllItems();
      reactionsController.setStickers(emojiPacks, emojiItems);
    }
  }

  public TGStickerObj modifyStickerObj (TGStickerObj sticker) {
    sticker.setDisplayScale(1f);

   // sticker.setDisplayScale(//sticker.isEmojiReaction() ?
   //   //getScaleForClassicEmojiReaction(sticker.getSticker(), getItemWidth() - Screen.dp(DEFAULT_STICKER_PADDING_DP * 2)) :
   //   TextMedia.getScale(sticker.getSticker(), getItemWidth() - Screen.dp(DEFAULT_STICKER_PADDING_DP * 2)));
    return sticker;
  }

  public static float getScaleForClassicEmojiReaction (@Nullable TdApi.Sticker sticker, int size) {
    if (sticker != null && Td.isAnimated(sticker.format) &&
      sticker.format.getConstructor() != TdApi.StickerFormatWebm.CONSTRUCTOR) {
      return 200.0f / 100.0f - (size != 0 ? Screen.dp(1f) * 2 / (float) size : 0);
    }
    return 1f;
  }



  /* * */

  private static class FakeControllerForBottomHeader extends ViewController<Void> {
    public FakeControllerForBottomHeader (@NonNull Context context, Tdlib tdlib) {
      super(context, tdlib);
    }

    @Override
    protected int getHeaderColorId () {
      return ColorId.filling;
    }

    @Override
    protected boolean useGraySearchHeader () {
      return true;
    }

    @Override
    protected View onCreateView (Context context) {
      return null;
    }

    @Override
    public int getId () {
      return 0;
    }
  }



  /* Animations */

  private static final int BOTTOM_HEADER_IN_SEARCH_MODE = 0;
  private static final int TOP_HEADER_IS_VISIBILITY = 1;

  private final BoolAnimator bottomHeaderSearchModeVisibility = new BoolAnimator(BOTTOM_HEADER_IN_SEARCH_MODE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220L, false);
  private final BoolAnimator topHeaderVisibility = new BoolAnimator(TOP_HEADER_IS_VISIBILITY, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 220L, false);

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == BOTTOM_HEADER_IN_SEARCH_MODE) {
      emojiTypesRecyclerView.setAlpha(factor);
    } else if (id == TOP_HEADER_IS_VISIBILITY) {
      topHeaderViewGroup.setTranslationY(-HeaderView.getSize(true) * (1f - factor));
      topHeaderViewGroup.setAlpha(factor);
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == BOTTOM_HEADER_IN_SEARCH_MODE) {
      if (finalFactor == 0) {
        emojiTypesRecyclerView.setVisibility(View.GONE);
      }
    } else if (id == TOP_HEADER_IS_VISIBILITY) {
      if (finalFactor == 0) {
        topHeaderViewGroup.setVisibility(View.GONE);
      }
    }
  }
}
