package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Date: 19/10/2016
 * Author: default
 */

public class MediaContentView extends FrameLayoutFix implements GestureDetector.OnGestureListener {
  private static final boolean TRACE_INTERCEPT = false;
  private static final boolean TRACE_TOUCHES = false;

  private MediaBottomBaseController<?> base;

  private final float touchSlop;
  private final GestureDetector flingDetector;
  private boolean blockTouch;

  public MediaContentView (Context context) {
    super(context);
    this.flingDetector = new GestureDetector(context, this);
    this.touchSlop = Screen.getTouchSlop();
  }

  public void setBoundController (MediaBottomBaseController<?> controller) {
    this.base = controller;
  }

  private int lastMeasuredWidth;
  private int lastMeasuredHeight;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();
    if (lastMeasuredWidth != width || lastMeasuredHeight != height) {
      lastMeasuredWidth = width;
      lastMeasuredHeight = height;
      base.onViewportChanged(width, height);
    }
  }

  private float touchStartY;
  private boolean shouldIntercept;
  private boolean intercepting;

  private float interceptStartY;

  private boolean isAnimating;

  private int currentScrollY;

  private void prepareIntercept (MotionEvent e) {
    currentScrollY = base.getRecyclerScrollY();

    scrollStartY = 0;
    scrolling = false;

    intercepting = false;
    isAnimating = base.isAnimating();
    touchStartY = e.getY();
    shouldIntercept = !isAnimating && base.canMoveRecycler() && base.isInsideRecyclerView(e.getX(), e.getY());
    blockTouch = base.shouldIgnoreCollapsing();
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        prepareIntercept(e);
        if (TRACE_INTERCEPT) {
          Log.v("intercept: %s %s", ViewUtils.getActionName(e), getState());
        }
        return isAnimating || super.onInterceptTouchEvent(e);
      }
      case MotionEvent.ACTION_MOVE: {
        if (TRACE_INTERCEPT) {
          Log.v("intercept: %s %s", ViewUtils.getActionName(e), getState());
        }
        if (intercepting || isAnimating) {
          return true;
        } else if (shouldIntercept) {
          float y = e.getY();
          float yDiff = y - touchStartY;
          if (Math.abs(yDiff) >= touchSlop && currentScrollY == 0) {
            intercepting = true;
            interceptStartY = y;
            base.onRecyclerMovementStarted();
            ((BaseActivity) getContext()).setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_TOUCHING_MEDIA_LAYOUT, true);
            return true;
          }
        }
        break;
      }
      default: {
        if (TRACE_INTERCEPT) {
          Log.v("intercept: %s %s", ViewUtils.getActionName(e), getState());
        }
        break;
      }
    }
    return super.onInterceptTouchEvent(e);
  }

  private boolean scrolling;
  private float scrollStartY;

  private String getState () {
    StringBuilder b = new StringBuilder();
    if (shouldIntercept) {
      b.append("shouldIntercept ");
    }
    if (intercepting) {
      b.append("intercepting ");
    }
    if (scrolling) {
      b.append("scrolling ");
    }
    if (isAnimating) {
      b.append("animating ");
    }
    return b.toString();
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (TRACE_TOUCHES) {
      Log.v("touch: %s %s", ViewUtils.getActionName(e), getState());
    }
    if (isAnimating) {
      return true;
    }

    if (!intercepting) {
      return super.onTouchEvent(e);
    }

    if (!blockTouch) {
      flingDetector.onTouchEvent(e);
    }

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        // Nothing to do?
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (blockTouch) {
          base.dispatchRecyclerTouchEvent(e);
        } else {
          if (scrolling) {
            if (e.getY() <= scrollStartY) {
              base.dispatchRecyclerTouchEvent(e);
              return true;
            } else {
              base.forceScrollRecyclerToTop();
              scrolling = false;
            }
          }
          if (base.moveRecyclerView(e.getY() - interceptStartY)) {
            if (!scrolling) {
              scrolling = true;
              scrollStartY = e.getY();
            }
          }
        }
        break;
      }

      case MotionEvent.ACTION_UP: {
        intercepting = false;
        base.dispatchRecyclerTouchEvent(e);
        if (!blockTouch) base.onRecyclerMovementFinished();
        ((BaseActivity) getContext()).setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_TOUCHING_MEDIA_LAYOUT, false);
        blockTouch = false;
        return true;
      }
    }

    if (scrolling) {
      base.dispatchRecyclerTouchEvent(e);
    }

    return true;
  }

  // fling

  @Override
  public boolean onDown (MotionEvent e) {
    return true;
  }

  @Override
  public void onShowPress (MotionEvent e) {

  }

  @Override
  public boolean onSingleTapUp (MotionEvent e) {
    return false;
  }

  @Override
  public boolean onScroll (MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
    return false;
  }

  @Override
  public void onLongPress (MotionEvent e) {

  }

  @Override
  public boolean onFling (MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    return Math.abs(velocityY) > Screen.dp(250, 1f) && base.handleFling(velocityY < 0);
  }
}
