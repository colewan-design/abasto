<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Resources\V1\AppNotificationResource;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;

/**
 * Budget alerts and bill reminders. Mobile clients read these on sync, which
 * is also what the Android notification worker surfaces.
 */
class NotificationController extends Controller
{
    public function index(Request $request): AnonymousResourceCollection
    {
        $user = $request->user();

        $notifications = $user->notifications()
            ->when($request->boolean('unread'), fn ($query) => $query->whereNull('read_at'))
            ->when($request->filled('since'), fn ($query) => $query->where('created_at', '>=', $request->date('since')))
            ->latest()
            ->paginate($request->integer('per_page', 30));

        return AppNotificationResource::collection($notifications)->additional([
            'meta' => ['unread' => $user->unreadNotifications()->count()],
        ]);
    }

    public function markRead(Request $request, string $notification): JsonResponse
    {
        $record = $request->user()->notifications()->whereKey($notification)->firstOrFail();

        $record->markAsRead();

        return response()->json([
            'data' => new AppNotificationResource($record->refresh()),
            'meta' => ['unread' => $request->user()->unreadNotifications()->count()],
        ]);
    }

    public function markAllRead(Request $request): JsonResponse
    {
        $request->user()->unreadNotifications()->update(['read_at' => now()]);

        return response()->json(['message' => 'All notifications marked as read.']);
    }

    public function destroy(Request $request, string $notification): JsonResponse
    {
        $request->user()->notifications()->whereKey($notification)->delete();

        return response()->json(['message' => 'Notification removed.']);
    }
}
