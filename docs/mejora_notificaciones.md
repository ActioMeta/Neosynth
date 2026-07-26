# Fixing Download Notifications

The issue you are experiencing with the download notifications is caused by a mismatch in Notification IDs within `DownloadWorker`.

Currently, the worker creates **two sets of notifications**:
1. A **Foreground Notification** (required by Android to keep the download alive) using a hardcoded ID `1001`. This notification has an indeterminate progress bar (the animated loading icon).
2. A **Progress Notification** using a unique ID per song (or playlist).

Because they use different IDs, the foreground notification (with the animated icon) is never updated with the real progress, and attempting to cancel it at the end fails because Android keeps the Foreground notification alive until all workers are truly finished. 

## Proposed Changes

### 1. Unify Notification IDs in `DownloadWorker.kt`
I will modify `getForegroundInfo()` to use the `notificationId` of the worker itself (or the batch ID if it's a playlist). 
This guarantees that the Foreground Notification is the **exact same** notification as the Progress Notification. 
* **Result**: The animated indeterminate icon will instantly change into an actual progress bar, and it will correctly update.

### 2. Graceful Completion
When a download finishes, the worker will update its own unified notification to a simple, static "Download Complete" state (removing the `setOngoing(true)` limit).
* **Result**: The animated icon will stop completely and show the checkmark.

### 3. Cleanup redundant cancel calls
I will remove `notificationManager.cancel(FOREGROUND_NOTIFICATION_ID)` as it's a hack that doesn't work correctly with WorkManager's lifecycle. WorkManager will handle the foreground state implicitly when we align the IDs.
