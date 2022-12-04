/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 18/08/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.util.InvalidateContentProvider;
import me.vkryl.core.lambda.Destroyable;

public class SmallChatView extends BaseView implements AttachDelegate, TooltipOverlayView.LocationProvider, InvalidateContentProvider, Destroyable {
  private final AvatarReceiver avatarReceiver;

  private DoubleTextWrapper chat;

  public SmallChatView (Context context, Tdlib tdlib) {
    super(context, tdlib);

    int viewHeight = Screen.dp(62f);
    this.avatarReceiver = new AvatarReceiver(this);
    layoutReceiver();
    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, viewHeight));
  }

  private void layoutReceiver () {
    int viewHeight = Screen.dp(62f);
    int radius = Screen.dp(50f) / 2;
    int left = Screen.dp(11f);
    int right = Screen.dp(11f) + radius * 2;
    int viewWidth = getMeasuredWidth();
    if (viewWidth == 0)
      return;
    if (Lang.rtl()) {
      this.avatarReceiver.setBounds(viewWidth - right, viewHeight / 2 - radius, viewWidth - left, viewHeight / 2 + radius);
    } else {
      this.avatarReceiver.setBounds(left, viewHeight / 2 - radius, right, viewHeight / 2 + radius);
    }
  }

  @Override
  public void attach () {
    avatarReceiver.attach();
  }

  @Override
  public void detach () {
    avatarReceiver.detach();
  }

  @Override
  public void performDestroy () {
    avatarReceiver.destroy();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    final int width = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
    if (width > 0 && chat != null) {
      chat.layout(width);
    }
    layoutReceiver();
  }

  public void setChat (DoubleTextWrapper chat) {
    if (this.chat == chat) {
      return;
    }

    if (this.chat != null) {
      this.chat.getViewProvider().detachFromView(this);
    }

    this.chat = chat;
    if (chat != null) {
      setPreviewChatId(null, chat.getChatId(), null);
    } else {
      clearPreviewChat();
    }

    if (chat != null) {
      final int currentWidth = getMeasuredWidth();
      if (currentWidth != 0) {
        chat.layout(currentWidth);
      }
      chat.getViewProvider().attachToView(this);
    }

    requestFile();
    invalidate();
  }

  private void requestFile () {
    if (chat != null) {
      avatarReceiver.requestMessageSender(tdlib, chat.getSenderId(), tdlib.needAvatarPreviewAnimation(chat.getSenderId()), false, true);
    } else {
      avatarReceiver.clear();
    }
  }

  @Override
  public boolean invalidateContent (Object cause) {
    if (this.chat == cause) {
      requestFile();
      return true;
    }
    return false;
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    if (chat != null) {
      chat.getTargetBounds(targetView, outRect);
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (chat == null) {
      return;
    }

    layoutReceiver();
    if (avatarReceiver.needPlaceholder()) {
      avatarReceiver.drawPlaceholder(c);
    }
    avatarReceiver.draw(c);

    chat.draw(this, avatarReceiver, c);
  }
}
