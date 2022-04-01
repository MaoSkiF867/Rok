/**
 * File created on 23/02/16 at 03:16
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.component.chat;

import android.graphics.Canvas;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageBotInfo;
import org.thunderdog.challegram.data.TGMessageChat;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;

public class MessagesTouchHelperCallback extends CustomTouchHelper.Callback {
  // public static final float SWIPE_MINIMUM_HEIGHT = 55f;
  public static final float SWIPE_THRESHOLD_WIDTH = 124f;

  private CustomTouchHelper helper;
  private MessagesController controller;

  public void setTouchHelper (CustomTouchHelper helper) {
    this.helper = helper;
  }

  public void setController (MessagesController controller) {
    this.controller = controller;
  }

  @Override
  public int getMovementFlags (RecyclerView recyclerView, RecyclerView.ViewHolder holder) {
    if (controller == null ||
        controller.inSelectMode() ||
        controller.navigationController() == null ||
        !MessagesHolder.isMessageType(holder.getItemViewType())) {
      return 0;
    }
    TGMessage m = MessagesHolder.findMessageView(holder.itemView).getMessage();
    if (!m.canSwipe() ||
        m instanceof TGMessageChat ||
        m instanceof TGMessageBotInfo ||
        m.isSending() ||
        m.getChatId() == 0) {
      return 0;
    }

    int flags = 0;

    if ((canDragReply() && m.canReplyTo()) || canQuickReact()) {
      int flag = Lang.rtl() ? ItemTouchHelper.RIGHT : ItemTouchHelper.LEFT;
      flags |= flag;
    }

    if (canDragShare() && m.canBeForwarded()) {
      flags |= Lang.rtl() ? ItemTouchHelper.LEFT : ItemTouchHelper.RIGHT;
    }

    if (canQuickReact()) {
      flags |= ItemTouchHelper.DOWN;
    }

    return makeMovementFlags(0, flags);
  }

  public boolean canDragReply () {
    return Settings.instance().needChatQuickReply() && controller.canWriteMessages() && !controller.needTabs();
  }

  public boolean canDragShare () {
    return Settings.instance().needChatQuickShare() && !controller.isSecretChat();
  }

  public boolean canQuickReact () {
    return Settings.instance().needQuickReaction() && !controller.needTabs() && controller.getChat() != null && U.safeLength(controller.getChat().availableReactions) > 0;
  }

  @Override
  public boolean isLongPressDragEnabled () {
    return false;
  }

  @Override
  public boolean isItemViewSwipeEnabled () {
    return false;
  }

  @Override
  public boolean onBeforeSwipe (RecyclerView.ViewHolder holder, int direction) {
    final TGMessage msg = MessagesHolder.findMessageView(holder.itemView).getMessage();
    if (msg.useBubbles() || msg.isTranslatedEnough()) {
      Runnable after = null;
      boolean needDelay = false;
      if (direction == (Lang.rtl() ? CustomTouchHelper.RIGHT : CustomTouchHelper.LEFT) && (canDragReply() || canQuickReact())) {
        after = () -> {
          if (msg.isTranslatedEnough() || !canDragReply()) {
            controller.quickReact(msg.getNewestMessage());
          } else {
            controller.showReply(msg.getNewestMessage(), true, true);
          }

          msg.prepareToReset();
        };
      }
      if (direction == (Lang.rtl() ? CustomTouchHelper.LEFT : CustomTouchHelper.RIGHT) && canDragShare()) {
        after = () -> controller.shareMessages(msg.getChatId(), msg.getAllMessages());
        needDelay = true;
      }
      msg.normalizeTranslation(holder.itemView, after, needDelay);
      return true;
    }
    return false;
  }

  @Override
  public float getSwipeThreshold (RecyclerView.ViewHolder holder) {
    float itemWidth = (float) holder.itemView.getMeasuredWidth();
    if (controller.getManager().useBubbles()) {
      return (float) Screen.dp(TGMessage.BUBBLE_MOVE_THRESHOLD) / itemWidth;
    } else {
      return (float) Screen.dp(SWIPE_THRESHOLD_WIDTH) / itemWidth * (Settings.instance().needChatQuickShare() ? 1 : 3);
    }
  }

  @Override
  public boolean onMove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
    return false;
  }

  @Override
  public void onSwiped (RecyclerView.ViewHolder holder, int swipeDir) {
    helper.ignoreSwipe(holder, swipeDir);
    TGMessage msg = MessagesHolder.findMessageView(holder.itemView).getMessage();
    if (msg.getTranslation() != 0f) {
      boolean needReaction = msg.isTranslatedEnough();
      msg.completeTranslation();
      msg.resetVertical();
      if (holder.itemView instanceof MessageViewGroup) {
        ((MessageViewGroup) holder.itemView).setSwipeTranslation(0f);
      }
      if (swipeDir == (Lang.rtl() ? CustomTouchHelper.RIGHT : CustomTouchHelper.LEFT)) {
        if (needReaction || !canDragReply()) {
          controller.quickReact(msg.getNewestMessage());
        } else if (canDragReply()) {
          controller.showReply(msg.getNewestMessage(), true, true);
        }
      } else {
        if (canDragShare()) {
          controller.shareMessages(msg.getChatId(), msg.getAllMessages());
        }
      }
    }
  }

  @Override
  public void onChildDraw (Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder holder, float dx, float dy, int state, boolean isActive) {
    if (state == ItemTouchHelper.ACTION_STATE_SWIPE && MessagesHolder.isMessageType(holder.getItemViewType())) {
      final MessageView v = MessagesHolder.findMessageView(holder.itemView);
      final TGMessage msg = v.getMessage();
      msg.translate(dx, true);
      if (isActive) {
        msg.translateVertical(dy);
      } else {
        msg.smoothResetVertical();
      }
      if (holder.itemView instanceof MessageViewGroup) {
        ((MessageViewGroup) holder.itemView).setSwipeTranslation(msg.getTranslation());
      }
    }
  }
}
