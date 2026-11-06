<?php

namespace App\Http\Controllers\Api\V1\Auth;

use App\Http\Controllers\Controller;
use App\Http\Resources\V1\UserResource;
use App\Services\DemoDataBuilder;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Symfony\Component\HttpKernel\Exception\AccessDeniedHttpException;

class DemoController extends Controller
{
    public function __construct(
        private readonly DemoDataBuilder $demo,
    ) {}

    /**
     * Throw away everything in the demo account and rebuild the sample data.
     */
    public function reset(Request $request): JsonResponse
    {
        $user = $request->user();

        if (! $user->isGuest()) {
            throw new AccessDeniedHttpException('Only the guest demo can be reset.');
        }

        $this->demo->reset($user);

        return response()->json([
            'message' => 'Demo data has been reset.',
            'user' => new UserResource($user->refresh()),
        ]);
    }
}
