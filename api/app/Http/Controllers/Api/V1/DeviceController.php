<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\V1\StoreDeviceRequest;
use App\Models\Device;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * Push token registration for due-date reminders and budget alerts.
 */
class DeviceController extends Controller
{
    public function store(StoreDeviceRequest $request): JsonResponse
    {
        $data = $request->validated();

        // A token identifies one install, so re-registering updates the row
        // rather than piling up duplicates after a reinstall.
        Device::query()
            ->withoutGlobalScopes()
            ->where('push_token', $data['push_token'])
            ->where('user_id', '!=', $request->user()->getKey())
            ->delete();

        $device = $request->user()->devices()->updateOrCreate(
            ['push_token' => $data['push_token']],
            [
                'platform' => $data['platform'],
                'name' => $data['name'] ?? null,
                'app_version' => $data['app_version'] ?? null,
                'last_seen_at' => now(),
            ],
        );

        return response()->json([
            'message' => 'Device registered.',
            'data' => [
                'id' => $device->getKey(),
                'platform' => $device->platform->value,
                'name' => $device->name,
            ],
        ], 201);
    }

    public function destroy(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'push_token' => ['required', 'string', 'max:512'],
        ]);

        $request->user()->devices()->where('push_token', $validated['push_token'])->delete();

        return response()->json(['message' => 'Device removed.']);
    }
}
