<?php

use App\Http\Controllers\Api\V1\AccountController;
use App\Http\Controllers\Api\V1\Auth\DemoController;
use App\Http\Controllers\Api\V1\Auth\GoogleAuthController;
use App\Http\Controllers\Api\V1\Auth\GuestAuthController;
use App\Http\Controllers\Api\V1\Auth\SessionController;
use App\Http\Controllers\Api\V1\BillController;
use App\Http\Controllers\Api\V1\BudgetController;
use App\Http\Controllers\Api\V1\CategoryController;
use App\Http\Controllers\Api\V1\CategoryGroupController;
use App\Http\Controllers\Api\V1\DeviceController;
use App\Http\Controllers\Api\V1\ExportController;
use App\Http\Controllers\Api\V1\NotificationController;
use App\Http\Controllers\Api\V1\OnboardingController;
use App\Http\Controllers\Api\V1\ProfileController;
use App\Http\Controllers\Api\V1\ReceiptController;
use App\Http\Controllers\Api\V1\ReceiptImageController;
use App\Http\Controllers\Api\V1\RecurringRuleController;
use App\Http\Controllers\Api\V1\ReportController;
use App\Http\Controllers\Api\V1\SavingsGoalController;
use App\Http\Controllers\Api\V1\TagController;
use App\Http\Controllers\Api\V1\TransactionController;
use App\Http\Controllers\Api\V1\TransferController;
use Illuminate\Support\Facades\Route;

Route::prefix('v1')->name('api.v1.')->group(function (): void {

    /*
    |--------------------------------------------------------------------------
    | Public
    |--------------------------------------------------------------------------
    */

    /*
     * The third throttle argument is a bucket name, and it is load-bearing.
     * Laravel keys an inline throttle on the route's *domain* and IP, not its
     * URI, so routes that declare no domain all share one counter per IP -
     * six guest sign-ins would lock someone out of login on their first try,
     * for the full hour the guest limiter decays over.
     */
    Route::post('auth/register', [SessionController::class, 'register'])
        ->middleware('throttle:6,1,register')
        ->name('auth.register');

    Route::post('auth/login', [SessionController::class, 'login'])
        ->middleware('throttle:6,1,login')
        ->name('auth.login');

    Route::post('auth/guest', [GuestAuthController::class, 'store'])
        ->middleware('throttle:10,60,guest')
        ->name('auth.guest');

    /*
     * Google sign-in works both signed out and while exploring as a guest, so
     * a guest can convert to a personal account without losing the session.
     */
    Route::post('auth/google', [GoogleAuthController::class, 'store'])
        ->middleware(['throttle:20,1,google', 'auth.optional:sanctum'])
        ->name('auth.google');

    /*
     * Receipt images are served from a signed URL so the <img> tag does not
     * need to carry a bearer token.
     */
    Route::get('receipts/{receipt}/image', [ReceiptImageController::class, 'show'])
        ->middleware(['signed', 'auth.optional:sanctum'])
        ->name('receipts.image');

    /*
    |--------------------------------------------------------------------------
    | Authenticated
    |--------------------------------------------------------------------------
    */

    Route::middleware(['auth:sanctum', 'active.user'])->group(function (): void {

        // Session and profile
        Route::post('auth/logout', [SessionController::class, 'logout'])->name('auth.logout');
        Route::post('auth/logout-all', [SessionController::class, 'logoutAll'])->name('auth.logout-all');
        Route::get('me', [ProfileController::class, 'show'])->name('me.show');
        Route::patch('me', [ProfileController::class, 'update'])->name('me.update');
        Route::delete('me', [ProfileController::class, 'destroy'])->name('me.destroy');

        // Guided setup
        Route::get('onboarding/options', [OnboardingController::class, 'options'])->name('onboarding.options');
        Route::post('onboarding/complete', [OnboardingController::class, 'complete'])->name('onboarding.complete');
        Route::post('onboarding/categories', [OnboardingController::class, 'seedCategories'])->name('onboarding.categories');

        // Guest demo
        Route::post('demo/reset', [DemoController::class, 'reset'])->name('demo.reset');

        // Accounts
        Route::post('accounts/recalculate', [AccountController::class, 'recalculate'])->name('accounts.recalculate');
        Route::get('accounts/{account}/transactions', [AccountController::class, 'transactions'])->name('accounts.transactions');
        Route::apiResource('accounts', AccountController::class);

        // Categories, groups and tags
        Route::post('categories/suggest', [CategoryController::class, 'suggest'])->name('categories.suggest');
        Route::apiResource('categories', CategoryController::class);
        Route::apiResource('category-groups', CategoryGroupController::class)->parameters([
            'category-groups' => 'category_group',
        ]);
        Route::apiResource('tags', TagController::class)->except('show');

        // Ledger
        Route::apiResource('transactions', TransactionController::class);
        Route::post('transfers', [TransferController::class, 'store'])->name('transfers.store');

        // Receipts
        Route::post('receipts/{receipt}/reprocess', [ReceiptController::class, 'reprocess'])->name('receipts.reprocess');
        Route::post('receipts/{receipt}/confirm', [ReceiptController::class, 'confirm'])->name('receipts.confirm');
        Route::apiResource('receipts', ReceiptController::class)->except('update');

        // Budgets
        Route::get('budget', [BudgetController::class, 'show'])->name('budget.show');
        Route::put('budget', [BudgetController::class, 'update'])->name('budget.update');
        Route::get('budget/suggestions', [BudgetController::class, 'suggestions'])->name('budget.suggestions');

        // Bills
        Route::get('bills/upcoming', [BillController::class, 'upcoming'])->name('bills.upcoming');
        Route::post('bills/{bill}/pay', [BillController::class, 'pay'])->name('bills.pay');
        Route::post('bills/{bill}/skip', [BillController::class, 'skip'])->name('bills.skip');
        Route::delete('bills/{bill}/payments/{payment}', [BillController::class, 'unpay'])->name('bills.unpay');
        Route::apiResource('bills', BillController::class);

        // Savings goals
        Route::post('savings-goals/{savings_goal}/contributions', [SavingsGoalController::class, 'contribute'])
            ->name('savings-goals.contribute');
        Route::delete('savings-goals/{savings_goal}/contributions/{contribution}', [SavingsGoalController::class, 'removeContribution'])
            ->name('savings-goals.contributions.destroy');
        Route::apiResource('savings-goals', SavingsGoalController::class)->parameters([
            'savings-goals' => 'savings_goal',
        ]);

        // Recurring transactions
        Route::post('recurring-rules/{recurring_rule}/run', [RecurringRuleController::class, 'run'])->name('recurring-rules.run');
        Route::apiResource('recurring-rules', RecurringRuleController::class)->parameters([
            'recurring-rules' => 'recurring_rule',
        ]);

        // Reports
        Route::prefix('reports')->name('reports.')->group(function (): void {
            Route::get('dashboard', [ReportController::class, 'dashboard'])->name('dashboard');
            Route::get('cash-flow', [ReportController::class, 'cashFlow'])->name('cash-flow');
            Route::get('categories', [ReportController::class, 'categories'])->name('categories');
            Route::get('trends', [ReportController::class, 'trends'])->name('trends');
            Route::get('merchants', [ReportController::class, 'merchants'])->name('merchants');
        });

        // Exports and backup
        Route::get('exports/transactions.csv', [ExportController::class, 'transactions'])->name('exports.transactions');
        Route::get('exports/report.pdf', [ExportController::class, 'report'])->name('exports.report');
        Route::get('exports/backup.json', [ExportController::class, 'backup'])->name('exports.backup');

        // Notifications and devices
        Route::get('notifications', [NotificationController::class, 'index'])->name('notifications.index');
        Route::post('notifications/read-all', [NotificationController::class, 'markAllRead'])->name('notifications.read-all');
        Route::post('notifications/{notification}/read', [NotificationController::class, 'markRead'])->name('notifications.read');
        Route::delete('notifications/{notification}', [NotificationController::class, 'destroy'])->name('notifications.destroy');

        Route::post('devices', [DeviceController::class, 'store'])->name('devices.store');
        Route::delete('devices', [DeviceController::class, 'destroy'])->name('devices.destroy');
    });
});
