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
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.SelectableItemDelegate;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.InvalidateContentProvider;

public class SmallChatView extends BaseView implements AttachDelegate, TooltipOverlayView.LocationProvider, InvalidateContentProvider, FactorAnimator.Target, SelectableItemDelegate {
  private final ImageReceiver receiver;

  private DoubleTextWrapper chat;

  public SmallChatView (Context context, Tdlib tdlib) {
    super(context, tdlib);

    int viewHeight = Screen.dp(62f);
    int radius = Screen.dp(50f) / 2;
    this.receiver = new ImageReceiver(this, radius);
    layoutReceiver();
    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, viewHeight));
  }

  private void layoutReceiver () {
    int viewHeight = Screen.dp(62f);
    int radius = Screen.dp(50f) / 2;
    int left = Screen.dp(11f);
    int right = Screen.dp(11f) + radius * 2;
    if (Lang.rtl()) {
      int viewWidth = getMeasuredWidth();
      this.receiver.setBounds(viewWidth - right, viewHeight / 2 - radius, viewWidth - left, viewHeight / 2 + radius);
    } else {
      this.receiver.setBounds(left, viewHeight / 2 - radius, right, viewHeight / 2 + radius);
    }
  }

  @Override
  public void attach () {
    receiver.attach();
  }

  @Override
  public void detach () {
    receiver.detach();
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
    receiver.requestFile(chat != null ? chat.getAvatarFile() : null);
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

  private float selectableFactor;

  public void setSelectionFactor (float selectableFactor, float selectionFactor) {
    if (this.selectableFactor != selectableFactor) {
      this.selectableFactor = selectableFactor;
      forceSelectionFactor(selectionFactor);
      invalidate();
    } else {
      forceSelectionFactor(selectionFactor);
    }
  }

  public void setSelectableFactor (float factor) {
    if (this.selectableFactor != factor) {
      this.selectableFactor = factor;
      invalidate();
    }
  }

  private float selectionFactor;
  private FactorAnimator animator;

  public void forceSelectionFactor (float factor) {
    if (animator != null) {
      animator.forceFactor(factor);
    }
    setSelectionFactor(factor);
  }

  public void animateFactor (float factor) {
    if (animator == null) {
      animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, selectionFactor);
    }
    animator.animateTo(factor);
  }

  @Override
  public void setIsItemSelected (boolean isSelected, int selectionIndex) {
    animateFactor(isSelected ? 1f : 0f);
  }

  private void setSelectionFactor (float factor) {
    if (this.selectionFactor != factor) {
      this.selectionFactor = factor;
      invalidate();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == 1) {
      invalidate();
    } else {
      setSelectionFactor(factor);
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  @Override
  protected void onDraw (Canvas c) {
    if (chat == null) {
      return;
    }
    int viewWidth = getMeasuredWidth();
    int viewHeight = getMeasuredHeight();

    final float selectionFactor = this.selectionFactor * this.selectableFactor;

    layoutReceiver();

    if (chat.getAvatarFile() != null) {
      if (receiver.needPlaceholder()) {
        receiver.drawPlaceholderRounded(c, receiver.getRadius());
      }
      receiver.draw(c);
    } else if (chat.getAvatarPlaceholder() != null) {
      chat.getAvatarPlaceholder().draw(c, receiver.centerX(), receiver.centerY());
    }

    chat.draw(this, receiver, c);

    if (this.selectableFactor != 0f) {
      final int centerX = viewWidth - Screen.dp(16f) - Screen.dp(10f);
      final int centerY = viewHeight / 2;

      if (selectionFactor != 0f) {
        SimplestCheckBox.draw(c, centerX, centerY, selectionFactor, null);
      }
    }
  }
}
