package org.thunderdog.challegram.component.reaction;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.EmojiToneHelper;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.EmojiData;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;

public class SelectableReactionView extends View {
  private static final int CHECK_MARK_MIN_SIZE = Screen.dp(10f);

  private boolean selected = false;
  private Drawable checkMarkDrawable;
  private final int padding;
  private final ImageReceiver imageReceiver;
  private final GifReceiver gifReceiver;
  private TGStickerObj sticker;
  private boolean isAnimation;
  private Path contour;
  private GifFile gifFile;

  private int stickerSize;
  private int stickerLeft;
  private int stickerTop;
  private int stickerRight;
  private int stickerBottom;

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight();
    int cx = viewWidth / 2;
    int cy = viewHeight / 2;

    // Draw emoji
    stickerSize = Math.min(viewWidth, viewHeight) - padding;
    stickerLeft = cx - stickerSize / 2;
    stickerTop = cy - stickerSize / 2;
    stickerRight = stickerLeft + stickerSize;
    stickerBottom = stickerTop + stickerSize;
    imageReceiver.setBounds(stickerLeft, stickerTop, stickerRight, stickerBottom);
    gifReceiver.setBounds(stickerLeft, stickerTop, stickerRight, stickerBottom);
  }

  public SelectableReactionView (Context context, int padding) {
    super(context);
    this.padding = padding;
    this.imageReceiver = new ImageReceiver(this, 0);
    this.gifReceiver = new GifReceiver(this);

    checkMarkDrawable = getResources().getDrawable(R.drawable.baseline_check_circle_24);
    int color = Theme.getColor(R.id.theme_color_bubbleOut_file);
    DrawableCompat.setTint(checkMarkDrawable, color);
  }

  public void setSticker (@Nullable TGStickerObj sticker) {
    this.sticker = sticker;
    ImageFile imageFile = sticker != null && !sticker.isEmpty() ? sticker.getImage() : null;
    gifFile = sticker != null && !sticker.isEmpty() ? sticker.getPreviewAnimation() : null;
    if ((sticker == null || sticker.isEmpty()) && imageFile != null) {
      throw new RuntimeException("");
    }
    contour = sticker != null ? sticker.getContour(Math.min(imageReceiver.getWidth(), imageReceiver.getHeight())) : null;
    imageReceiver.requestFile(imageFile);
    if (gifFile != null) {
      gifFile.setPlayOnce(true);
    }
    gifReceiver.requestFile(gifFile);
  }

  public void startAnimation () {
    if (gifFile != null) {
      gifFile.setLooped(false);
    }
    this.isAnimation = sticker != null && sticker.isAnimated();
  }

  public void stopAnimation () {
    this.isAnimation = false;
  }

  public void setReactionSelected (boolean value, boolean animated) {
    if (value == selected) return;
    selected = value;
    if (selected && animated) {
      startAnimation();
    }
    invalidate();
  }

  public boolean selected () {
    return selected;
  }

  @Override
  protected void onDraw (Canvas c) {

    contour = sticker != null ? sticker.getContour(Math.min(imageReceiver.getWidth(), imageReceiver.getHeight())) : null;
    if (isAnimation) {
      if (gifReceiver.needPlaceholder()) {
        if (imageReceiver.needPlaceholder()) {
          imageReceiver.drawPlaceholderContour(c, contour);
        }
        imageReceiver.draw(c);
      }
      gifReceiver.draw(c);
    } else {
      if (imageReceiver.needPlaceholder()) {
        imageReceiver.drawPlaceholderContour(c, contour);
      }
      imageReceiver.draw(c);
    }

    // Draw check mark
    if (selected) {
      int markSize = Math.max(CHECK_MARK_MIN_SIZE, stickerSize / 2);
      int markRight = stickerRight + markSize / 2;
      int markBottom = stickerBottom + markSize / 2;
      int markLeft = markRight - markSize;
      int markTop = markBottom - markSize;
      checkMarkDrawable.setBounds(markLeft, markTop, markRight, markBottom);
      checkMarkDrawable.draw(c);
    }
  }
}
