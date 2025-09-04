package com.example.dbeventservice.service;

import com.example.dbeventservice.model.*;
import com.example.dbeventservice.repositories.*;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// NotificationService.java (add imports)
import com.google.firebase.messaging.*;
import java.time.Instant;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    //Is about the the notfication about the promoter
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    //----------------------------------------

    private final FCMTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final EventRepository eventRepo;
    private final TicketRepository ticketRepo;
    private final ResalePostRepository resaleRepo;
    private final FollowRepository followRepo;
    private final EventNotifyStateRepository notifyStateRepo;

    private final ResaleOrderRepository resaleOrderRepo;

    // ---------- Firebase boot ----------
    private void initFirebaseIfNeeded() throws Exception {
        if (!FirebaseApp.getApps().isEmpty()) return;
        InputStream serviceAccount = getClass().getClassLoader().getResourceAsStream("firebase_key.json");
        if (serviceAccount == null) throw new IllegalStateException("firebase_key.json missing from resources");
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();
        FirebaseApp.initializeApp(options);
    }

    // ---------- Low-level senders ----------
    public void sendPush(String token, String title, String body) {
        try {
            initFirebaseIfNeeded();
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .build();
            FirebaseMessaging.getInstance().send(message);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void sendPushWithData(String token, String title, String body, Map<String,String> data) {
        try {
            initFirebaseIfNeeded();
            Message.Builder b = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build());
            if (data != null) data.forEach(b::putData);
            FirebaseMessaging.getInstance().send(b.build());
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ---------- Helpers ----------
    private String displayNameOf(User u) {
        String first = Optional.ofNullable(u.getFirstName()).orElse("");
        String last  = Optional.ofNullable(u.getLastName()).orElse("");
        String dn = (first + " " + last).trim();
        return dn.isBlank() ? u.getUsername() : dn;
    }

    private Map<String,String> socialData(String kind, Long eventId) {
        Map<String,String> m = new HashMap<>();
        m.put("type", "SOCIAL");        // iOS reads this to open NotificationsView
        m.put("kind", kind);
        if (eventId != null) m.put("eventId", String.valueOf(eventId));
        return m;
    }

    private long soldCount(Long eventId) {
        return ticketRepo.countPaidTicketsByEventId(eventId);
    }

    /** Capacity = SOLD + AVAILABLE (since your Event stores only available) */
    private long capacityForEvent(Long eventId, long sold) {
        var event = eventRepo.findById(eventId).orElseThrow();
        long available = Optional.ofNullable(event.getAvailableTickets()).map(Integer::longValue).orElse(0L);
        long cap = available + sold;

        // Defensive: if still 0, sum tiers
        if (cap == 0 && event.getTicketTiers() != null) {
            long tierAvail = event.getTicketTiers().stream()
                    .map(TicketTier::getAvailableQuantity)
                    .filter(Objects::nonNull)
                    .mapToLong(Integer::longValue)
                    .sum();
            cap = tierAvail + sold;
        }
        return cap;
    }

    /** Seller confirmed a manual order → notify the buyer it’s done */
    public void notifyManualPaymentConfirmed(Long orderId) {
        ResaleOrder order = resaleOrderRepo.findById(orderId).orElseThrow();
        if (order.getResalePost() == null) return;

        var post   = order.getResalePost();
        var buyer  = order.getBuyer();
        var ticket = post.getTicket();
        var event  = (ticket != null) ? ticket.getEvent() : null;

        if (buyer == null) return;

        String eventTitle = (event != null && event.getTitle() != null) ? event.getTitle() : "your event";

        Map<String,String> data = socialData("MANUAL_RESALE_CONFIRMED", event != null ? event.getId() : null);
        data.put("orderId", String.valueOf(order.getId()));
        data.put("resaleId", String.valueOf(post.getId()));

        sendPushToUsernameWithData(
                buyer.getUsername(),
                "✅ Resale confirmed",
                "Your resale purchase for \"" + eventTitle + "\" is confirmed.",
                data
        );
    }


    // ---------- High-level business notifications ----------

    /** Owner gets a push when someone purchases a ticket */
    public void notifyOwnerTicketPurchased(Long eventId, Long buyerId) {
        var event = eventRepo.findById(eventId).orElseThrow();
        var owner = event.getCreatedBy();
        var buyer = userRepo.findById(buyerId).orElseThrow();
        if (owner == null) return;

        String title = "🎟️ New ticket sold";
        String body  = displayNameOf(buyer) + " bought a ticket for \"" + event.getTitle() + "\"";
        sendPushToUsernameWithData(owner.getUsername(), title, body, socialData("TICKET_PURCHASED", eventId));
    }

    /** Buyer created a manual-payment resale order → notify the seller to review/confirm */
    public void notifyManualPaymentPending(Long orderId) {
        ResaleOrder order = resaleOrderRepo.findById(orderId).orElseThrow();
        if (order.getResalePost() == null) return;

        var post   = order.getResalePost();
        var seller = post.getSeller();
        var ticket = post.getTicket();
        var event  = (ticket != null) ? ticket.getEvent() : null;
        var buyer  = order.getBuyer();

        if (seller == null) return;

        String eventTitle = (event != null && event.getTitle() != null) ? event.getTitle() : "your event";
        String methodName = (order.getMethod() != null) ? order.getMethod().name() : "MANUAL";

        String title = "⏳ Manual payment pending";
        String body  = (buyer != null ? (displayNameOf(buyer) + " ") : "")
                + "requested review (" + methodName + ") for \"" + eventTitle + "\"";

        Map<String,String> data = socialData("MANUAL_RESALE_PENDING", event != null ? event.getId() : null);
        data.put("orderId", String.valueOf(order.getId()));
        data.put("resaleId", String.valueOf(post.getId()));

        sendPushToUsernameWithData(seller.getUsername(), title, body, data);
    }


    /** Check 50/80/100% milestones and notify owner (send each once) */
    @Transactional
    public void checkAndNotifyMilestones(Long eventId) {
        var event = eventRepo.findById(eventId).orElseThrow();
        if (event.getCreatedBy() == null) return;

        long sold = soldCount(eventId);
        long capacity = capacityForEvent(eventId, sold);
        if (capacity <= 0) return;

        double pct = (double) sold / (double) capacity;

        EventNotifyState state = notifyStateRepo.findById(eventId)
                .orElseGet(() -> new EventNotifyState(eventId, false, false, false));

        boolean changed = false;
        if (pct >= 0.50 && !state.isSent50()) { sendMilestone(eventId, "50%");   state.setSent50(true);  changed = true; }
        if (pct >= 0.80 && !state.isSent80()) { sendMilestone(eventId, "80%");   state.setSent80(true);  changed = true; }
        if (pct >= 1.00 && !state.isSent100()){ sendMilestone(eventId, "Sold out"); state.setSent100(true); changed = true; }
        if (changed) notifyStateRepo.save(state);
    }

    private void sendMilestone(Long eventId, String label) {
        var event = eventRepo.findById(eventId).orElseThrow();
        var owner = event.getCreatedBy();
        if (owner == null) return;

        String title = "📈 " + label + " reached";
        String body  = "\"" + event.getTitle() + "\" is now " + label + "!";
        sendPushToUsernameWithData(owner.getUsername(), title, body, socialData("EVENT_MILESTONE", eventId));
    }

    /** Resale sold → notify seller and (optionally) owner with buyer (if detectable) */
    public void notifyResaleSold(Long resalePostId) {
        ResalePost resale = resaleRepo.findById(resalePostId).orElseThrow();
        Ticket ticket = resale.getTicket();
        if (ticket == null || ticket.getEvent() == null) return;

        var event  = ticket.getEvent();
        var owner  = event.getCreatedBy();
        var seller = resale.getSeller();

        // Try to infer buyer from ticket owner if ownership changed away from seller
        User buyer = null;
        if (ticket.getOwner() != null && seller != null && !Objects.equals(ticket.getOwner().getId(), seller.getId())) {
            buyer = ticket.getOwner();
        }

        var data = socialData("RESALE_SOLD", event.getId());
        data.put("resaleId", String.valueOf(resalePostId));

        // Seller
        if (seller != null) {
            sendPushToUsernameWithData(
                    seller.getUsername(),
                    "💸 Your resale sold!",
                    "Your ticket for \"" + event.getTitle() + "\" has been sold.",
                    data
            );
        }

        // Owner (optional)
        if (owner != null) {
            String body = (buyer != null)
                    ? displayNameOf(buyer) + " purchased a resale for \"" + event.getTitle() + "\""
                    : "A resale was purchased for \"" + event.getTitle() + "\"";
            sendPushToUsernameWithData(owner.getUsername(), "♻️ A resale just sold", body, data);
        }
    }

    /** “Ivan is going to …” → notify followers of the user who just became attending */
    public void notifyFriendsGoing(Long userId, Long eventId) {
        var user  = userRepo.findById(userId).orElseThrow();
        var event = eventRepo.findById(eventId).orElseThrow();

        List<User> followers = followRepo.findAcceptedFollowers(user); // people who follow `user`
        if (followers.isEmpty()) return;

        String title = "👋 " + displayNameOf(user) + " is going";
        String body  = "They’re attending \"" + event.getTitle() + "\"";

        List<String> usernames = followers.stream().map(User::getUsername).collect(Collectors.toList());
        sendPushToUsernamesWithData(usernames, title, body, socialData("FRIEND_GOING", eventId));
    }

    /** New resale listed on a “hot” event → notify an audience (e.g. owner’s followers) */
    public void notifyHotEventResaleAdded(Long eventId, Long resalePostId, Long listerUserId) {
        if (!isHot(eventId)) return;

        var event = eventRepo.findById(eventId).orElseThrow();
        var owner = event.getCreatedBy();

        // Audience strategy: followers of the event owner (change if you want).
        List<User> audience = (owner != null) ? followRepo.findAcceptedFollowers(owner) : List.of();
        if (audience.isEmpty()) return;

        String title = "🔥 New resale listed";
        String body  = "New ticket available for \"" + event.getTitle() + "\"";

        Map<String,String> data = socialData("HOT_EVENT_RESALE_ADDED", eventId);
        data.put("resaleId", String.valueOf(resalePostId));

        List<String> usernames = audience.stream().map(User::getUsername).collect(Collectors.toList());
        sendPushToUsernamesWithData(usernames, title, body, data);
    }

    /** Hot if >=80% sold or <=20 tickets left (based on SOLD+AVAILABLE) */
    public boolean isHot(Long eventId) {
        long sold = soldCount(eventId);
        long capacity = capacityForEvent(eventId, sold);
        if (capacity <= 0) return false;

        long left = capacity - sold;
        double pct = (double) sold / (double) capacity;
        return pct >= 0.80 || left <= 20;
    }



    // --- APNs / Android configs ---
    private ApnsConfig apnsConfig(String collapseId) {
        ApnsConfig.Builder b = ApnsConfig.builder()
                .putHeader("apns-priority", "10")          // immediate delivery
                .putHeader("apns-push-type", "alert")      // required on iOS 13+
                .putHeader("apns-expiration", String.valueOf(Instant.now().plusSeconds(3600).getEpochSecond()))
                .setAps(Aps.builder().setSound("default").build());
        if (collapseId != null) b.putHeader("apns-collapse-id", collapseId);
        return b.build();
    }

    private AndroidConfig androidConfig() {
        return AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setTtl(3600_000) // 1h
                .build();
    }

    // --- Multicast sender with cleanup & timing ---
    private void sendMulticast(List<String> tokens, String title, String body, Map<String,String> data, String collapseId) {
        if (tokens == null || tokens.isEmpty()) return;

        try {
            long t0 = System.currentTimeMillis();
            MulticastMessage.Builder mb = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .setApnsConfig(apnsConfig(collapseId))
                    .setAndroidConfig(androidConfig());
            if (data != null) data.forEach(mb::putData);

            BatchResponse br = FirebaseMessaging.getInstance().sendEachForMulticast(mb.build());
            long took = System.currentTimeMillis() - t0;
            System.out.printf("FCM multicast to %d tokens → success=%d, took %d ms%n",
                    tokens.size(), br.getSuccessCount(), took);

            // prune invalid tokens so future sends are faster
            for (int i = 0; i < br.getResponses().size(); i++) {
                SendResponse resp = br.getResponses().get(i);
                if (!resp.isSuccessful()) {
                    FirebaseMessagingException fme = resp.getException(); // may be null
                    if (fme != null) {
                        MessagingErrorCode code = fme.getMessagingErrorCode();
                        if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                            try {
                                tokenRepo.deleteByToken(tokens.get(i));
                            } catch (Exception ignore) {}
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendPushToUsername(String username, String title, String body) {
        var tokens = tokenRepo.findAllByUsername(username).stream().map(FCMToken::getToken).toList();
        sendMulticast(tokens, title, body, null, null);
    }

    public void sendPushToUsernameWithData(String username, String title, String body, Map<String,String> data) {
        var tokens = tokenRepo.findAllByUsername(username).stream().map(FCMToken::getToken).toList();
        String collapse = data != null && data.containsKey("eventId") ? "event-" + data.get("eventId") : null;
        sendMulticast(tokens, title, body, data, collapse);
    }

    public void sendPushToUsernamesWithData(Collection<String> usernames, String title, String body, Map<String,String> data) {
        if (usernames == null || usernames.isEmpty()) return;
        // add this repo method (below)
        var tokens = tokenRepo.findAllByUsernameIn(usernames).stream().map(FCMToken::getToken).toList();
        String collapse = data != null && data.containsKey("eventId") ? "event-" + data.get("eventId") : null;
        sendMulticast(tokens, title, body, data, collapse);
    }

    public void notifyPromoterAssigned(Long eventId, Long promoterUserId) {
        try {
            // TODO: replace with your real notifier (push, email, in-app)
            log.info("📣 User {} assigned as promoter for event {}", promoterUserId, eventId);
            // Example: create in-app notification entity, or send FCM/email, etc.
        } catch (Exception e) {
            log.warn("Failed to notify promoter assignment", e);
        }
    }


}
