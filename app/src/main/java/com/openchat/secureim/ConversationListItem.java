package com.openchat.secureim;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;

import com.openchat.secureim.components.AlertView;
import com.openchat.secureim.components.AvatarImageView;
import com.openchat.secureim.components.DeliveryStatusView;
import com.openchat.secureim.components.FromTextView;
import com.openchat.secureim.components.ThumbnailView;
import com.openchat.secureim.crypto.MasterSecret;
import com.openchat.secureim.database.model.ThreadRecord;
import com.openchat.secureim.mms.GlideRequests;
import com.openchat.secureim.recipients.Recipient;
import com.openchat.secureim.recipients.RecipientModifiedListener;
import com.openchat.secureim.util.DateUtils;
import com.openchat.secureim.util.Util;
import com.openchat.secureim.util.ViewUtil;

import java.util.Locale;
import java.util.Set;

import static com.openchat.secureim.util.SpanUtil.color;

public class ConversationListItem extends RelativeLayout
                                  implements RecipientModifiedListener,
                                             BindableConversationListItem, Unbindable
{
  @SuppressWarnings("unused")
  private final static String TAG = ConversationListItem.class.getSimpleName();

  private final static Typeface BOLD_TYPEFACE  = Typeface.create("sans-serif", Typeface.BOLD);
  private final static Typeface LIGHT_TYPEFACE = Typeface.create("sans-serif-light", Typeface.NORMAL);

  private Set<Long>          selectedThreads;
  private Recipient          recipient;
  private long               threadId;
  private GlideRequests      glideRequests;
  private TextView           subjectView;
  private FromTextView       fromView;
  private TextView           dateView;
  private TextView           archivedView;
  private DeliveryStatusView deliveryStatusIndicator;
  private AlertView          alertView;
  private ImageView          unreadIndicator;
  private long               lastSeen;

  private int             unreadCount;
  private AvatarImageView contactPhotoImage;
  private ThumbnailView   thumbnailView;

  private int distributionType;

  public ConversationListItem(Context context) {
    this(context, null);
  }

  public ConversationListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.subjectView             = findViewById(R.id.subject);
    this.fromView                = findViewById(R.id.from);
    this.dateView                = findViewById(R.id.date);
    this.deliveryStatusIndicator = findViewById(R.id.delivery_status);
    this.alertView               = findViewById(R.id.indicators_parent);
    this.contactPhotoImage       = findViewById(R.id.contact_photo_image);
    this.thumbnailView           = findViewById(R.id.thumbnail);
    this.archivedView            = findViewById(R.id.archived);
    this.unreadIndicator         = findViewById(R.id.unread_indicator);
    thumbnailView.setClickable(false);

    ViewUtil.setTextViewGravityStart(this.fromView, getContext());
    ViewUtil.setTextViewGravityStart(this.subjectView, getContext());
  }

  @Override
  public void bind(@NonNull MasterSecret masterSecret, @NonNull ThreadRecord thread,
                   @NonNull GlideRequests glideRequests, @NonNull Locale locale,
                   @NonNull Set<Long> selectedThreads, boolean batchMode)
  {
    this.selectedThreads  = selectedThreads;
    this.recipient        = thread.getRecipient();
    this.threadId         = thread.getThreadId();
    this.glideRequests    = glideRequests;
    this.unreadCount      = thread.getUnreadCount();
    this.distributionType = thread.getDistributionType();
    this.lastSeen         = thread.getLastSeen();

    this.recipient.addListener(this);
    this.fromView.setText(recipient, unreadCount == 0);

    this.subjectView.setText(thread.getDisplayBody());
//    this.subjectView.setTypeface(read ? LIGHT_TYPEFACE : BOLD_TYPEFACE);

    if (thread.getDate() > 0) {
      CharSequence date = DateUtils.getBriefRelativeTimeSpanString(getContext(), locale, thread.getDate());
      dateView.setText(unreadCount == 0 ? date : color(getResources().getColor(R.color.textsecure_primary_dark), date));
      dateView.setTypeface(unreadCount == 0 ? LIGHT_TYPEFACE : BOLD_TYPEFACE);
    }

    if (thread.isArchived()) {
      this.archivedView.setVisibility(View.VISIBLE);
    } else {
      this.archivedView.setVisibility(View.GONE);
    }

    setStatusIcons(thread);
    setThumbnailSnippet(masterSecret, thread);
    setBatchState(batchMode);
    setRippleColor(recipient);
    setUnreadIndicator(thread);
    this.contactPhotoImage.setAvatar(glideRequests, recipient, true);
  }

  @Override
  public void unbind() {
    if (this.recipient != null) this.recipient.removeListener(this);
  }

  private void setBatchState(boolean batch) {
    setSelected(batch && selectedThreads.contains(threadId));
  }

  public Recipient getRecipient() {
    return recipient;
  }

  public long getThreadId() {
    return threadId;
  }

  public int getUnreadCount() {
    return unreadCount;
  }

  public int getDistributionType() {
    return distributionType;
  }

  public long getLastSeen() {
    return lastSeen;
  }

  private void setThumbnailSnippet(MasterSecret masterSecret, ThreadRecord thread) {
    if (thread.getSnippetUri() != null) {
      this.thumbnailView.setVisibility(View.VISIBLE);
      this.thumbnailView.setImageResource(masterSecret, glideRequests, thread.getSnippetUri());

      LayoutParams subjectParams = (RelativeLayout.LayoutParams)this.subjectView.getLayoutParams();
      subjectParams.addRule(RelativeLayout.LEFT_OF, R.id.thumbnail);
      if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
        subjectParams.addRule(RelativeLayout.START_OF, R.id.thumbnail);
      }
      this.subjectView.setLayoutParams(subjectParams);
      this.post(new ThumbnailPositioner(thumbnailView, archivedView, deliveryStatusIndicator, dateView));
    } else {
      this.thumbnailView.setVisibility(View.GONE);

      LayoutParams subjectParams = (RelativeLayout.LayoutParams)this.subjectView.getLayoutParams();
      subjectParams.addRule(RelativeLayout.LEFT_OF, R.id.status);
      if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
        subjectParams.addRule(RelativeLayout.START_OF, R.id.status);
      }
      this.subjectView.setLayoutParams(subjectParams);
    }
  }

  private void setStatusIcons(ThreadRecord thread) {
    if (!thread.isOutgoing() || thread.isOutgoingCall() || thread.isVerificationStatusChange()) {
      deliveryStatusIndicator.setNone();
      alertView.setNone();
    } else if (thread.isFailed()) {
      deliveryStatusIndicator.setNone();
      alertView.setFailed();
    } else if (thread.isPendingInsecureSmsFallback()) {
      deliveryStatusIndicator.setNone();
      alertView.setPendingApproval();
    } else {
      alertView.setNone();

      if      (thread.isPending())    deliveryStatusIndicator.setPending();
      else if (thread.isRemoteRead()) deliveryStatusIndicator.setRead();
      else if (thread.isDelivered())  deliveryStatusIndicator.setDelivered();
      else                            deliveryStatusIndicator.setSent();
    }
  }

  private void setRippleColor(Recipient recipient) {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      ((RippleDrawable)(getBackground()).mutate())
          .setColor(ColorStateList.valueOf(recipient.getColor().toConversationColor(getContext())));
    }
  }

  private void setUnreadIndicator(ThreadRecord thread) {
    if (thread.isOutgoing() || thread.getUnreadCount() == 0) {
      unreadIndicator.setVisibility(View.GONE);
      return;
    }

    unreadIndicator.setImageDrawable(TextDrawable.builder()
                                                 .beginConfig()
                                                 .width(ViewUtil.dpToPx(getContext(), 24))
                                                 .height(ViewUtil.dpToPx(getContext(), 24))
                                                 .textColor(Color.WHITE)
                                                 .bold()
                                                 .endConfig()
                                                 .buildRound(String.valueOf(thread.getUnreadCount()), getResources().getColor(R.color.textsecure_primary_dark)));
    unreadIndicator.setVisibility(View.VISIBLE);
  }

  @Override
  public void onModified(final Recipient recipient) {
    Util.runOnMain(() -> {
      fromView.setText(recipient, unreadCount == 0);
      contactPhotoImage.setAvatar(glideRequests, recipient, true);
      setRippleColor(recipient);
    });
  }

  private static class ThumbnailPositioner implements Runnable {

    private final View thumbnailView;
    private final View archivedView;
    private final View deliveryStatusView;
    private final View dateView;

    ThumbnailPositioner(View thumbnailView, View archivedView, View deliveryStatusView, View dateView) {
      this.thumbnailView      = thumbnailView;
      this.archivedView       = archivedView;
      this.deliveryStatusView = deliveryStatusView;
      this.dateView           = dateView;
    }

    @Override
    public void run() {
      LayoutParams thumbnailParams = (RelativeLayout.LayoutParams)thumbnailView.getLayoutParams();

      if (archivedView.getVisibility() == View.VISIBLE &&
          (archivedView.getWidth() + deliveryStatusView.getWidth()) > dateView.getWidth())
      {
        thumbnailParams.addRule(RelativeLayout.LEFT_OF, R.id.status);
        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
          thumbnailParams.addRule(RelativeLayout.START_OF, R.id.status);
        }
      } else {
        thumbnailParams.addRule(RelativeLayout.LEFT_OF, R.id.date);
        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
          thumbnailParams.addRule(RelativeLayout.START_OF, R.id.date);
        }
      }

      thumbnailView.setLayoutParams(thumbnailParams);
    }
  }

}
