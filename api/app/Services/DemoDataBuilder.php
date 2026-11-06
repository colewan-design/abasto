<?php

namespace App\Services;

use App\Enums\AccountType;
use App\Enums\BillRecurrence;
use App\Enums\CreatedVia;
use App\Enums\ReceiptStatus;
use App\Enums\RecurrenceFrequency;
use App\Enums\TransactionDirection;
use App\Enums\TransactionType;
use App\Models\Account;
use App\Models\Category;
use App\Models\Receipt;
use App\Models\SavingsGoal;
use App\Models\Transaction;
use App\Models\User;
use App\Services\Receipts\DuplicateDetector;
use Illuminate\Support\Carbon;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;

/**
 * Fills a guest account with a few months of believable sample data.
 *
 * Everything written here is flagged `is_demo` and belongs to a user with
 * `is_demo = true`, so it lives in its own account and can never mix with a
 * real person's records. Signing in creates a separate, empty personal
 * account; resetting wipes and rebuilds only this data.
 */
class DemoDataBuilder
{
    public function __construct(
        private readonly OnboardingService $onboarding,
        private readonly BudgetService $budgets,
        private readonly SavingsGoalService $savingsGoals,
        private readonly AccountBalanceService $balances,
        private readonly DuplicateDetector $duplicates,
    ) {}

    public function build(User $user): void
    {
        DB::transaction(function () use ($user): void {
            $currency = (string) config('budget.demo.currency');

            $user->forceFill([
                'currency_code' => $currency,
                'onboarding_completed_at' => now(),
                'demo_seeded_at' => now(),
            ])->save();

            $this->onboarding->seedCategories($user);

            $accounts = $this->accounts($user, $currency);
            $categories = $user->categories()->get()->keyBy('name');

            $this->transactions($user, $accounts, $categories);
            $this->savings($user, $accounts, $categories);
            $this->bills($user, $accounts, $categories);
            $this->recurringRules($user, $accounts, $categories);
            $this->budget($user, $categories);
            $this->receipts($user, $accounts, $categories);

            foreach ($accounts as $account) {
                $this->balances->recalculate($account);
            }
        });
    }

    /**
     * Wipe the sample data and build it again.
     */
    public function reset(User $user): void
    {
        DB::transaction(function () use ($user): void {
            $user->receipts()->withTrashed()->get()->each(function (Receipt $receipt): void {
                Storage::disk(config('budget.receipts.disk'))->delete(array_values(array_filter([
                    $receipt->image_path,
                    $receipt->thumbnail_path,
                ])));
            });

            $user->transactions()->withTrashed()->forceDelete();
            $user->receipts()->withTrashed()->forceDelete();
            $user->bills()->delete();
            $user->savingsGoals()->delete();
            $user->recurringRules()->delete();
            $user->budgets()->delete();
            $user->merchantCategoryRules()->delete();
            $user->categories()->delete();
            $user->categoryGroups()->delete();
            $user->tags()->delete();
            $user->accounts()->delete();
        });

        $this->build($user);
    }

    /**
     * @return Collection<string, Account>
     */
    private function accounts(User $user, string $currency): Collection
    {
        $definitions = [
            ['name' => 'Cash Wallet', 'type' => AccountType::Cash, 'opening' => 350000, 'color' => '#22c55e'],
            ['name' => 'Savings Account', 'type' => AccountType::Bank, 'opening' => 4280000, 'color' => '#2563eb', 'institution' => 'Demo Bank'],
            ['name' => 'E-Wallet', 'type' => AccountType::Ewallet, 'opening' => 125000, 'color' => '#a855f7'],
            ['name' => 'Credit Card', 'type' => AccountType::CreditCard, 'opening' => -840000, 'color' => '#ef4444', 'limit' => 5000000],
        ];

        $accounts = collect();

        foreach ($definitions as $order => $definition) {
            $accounts[$definition['name']] = $user->accounts()->create([
                'name' => $definition['name'],
                'type' => $definition['type']->value,
                'currency_code' => $currency,
                'opening_balance_minor' => $definition['opening'],
                'credit_limit_minor' => $definition['limit'] ?? null,
                'institution' => $definition['institution'] ?? null,
                'color' => $definition['color'],
                'icon' => $definition['type']->defaultIcon(),
                'sort_order' => $order,
            ]);
        }

        return $accounts;
    }

    /**
     * @param  Collection<string, Account>  $accounts
     * @param  Collection<string, Category>  $categories
     */
    private function transactions(User $user, Collection $accounts, Collection $categories): void
    {
        $months = (int) config('budget.demo.months_of_history');
        $start = today()->copy()->subMonths($months - 1)->startOfMonth();

        for ($month = 0; $month < $months; $month++) {
            $cursor = $start->copy()->addMonths($month);
            $isCurrentMonth = $cursor->isSameMonth(today());

            $this->income($user, $accounts, $categories, $cursor);
            $this->fixedCosts($user, $accounts, $categories, $cursor);
            $this->groceries($user, $accounts, $categories, $cursor, $isCurrentMonth);
            $this->dailyLife($user, $accounts, $categories, $cursor, $isCurrentMonth);
            $this->transfers($user, $accounts, $cursor);
        }
    }

    /**
     * @param  Collection<string, Account>  $accounts
     * @param  Collection<string, Category>  $categories
     */
    private function income(User $user, Collection $accounts, Collection $categories, Carbon $month): void
    {
        // Paid on the 1st and the 16th. Anchoring the first payday to the 1st
        // means the current month always has income on it, however early in
        // the month the demo is opened.
        foreach ([1, 16] as $day) {
            $date = $this->dayIn($month, $day);

            if ($date->isAfter(today())) {
                continue;
            }

            $this->write($user, [
                'account' => $accounts['Savings Account'],
                'category' => $categories['Salary'] ?? null,
                'type' => TransactionType::Income,
                'amount' => 2750000,
                'date' => $date,
                'merchant' => 'Acme Corporation',
                'note' => 'Semi-monthly payroll',
            ]);
        }

        if ($month->month === 12) {
            $this->write($user, [
                'account' => $accounts['Savings Account'],
                'category' => $categories['Bonus'] ?? null,
                'type' => TransactionType::Income,
                'amount' => 2750000,
                'date' => $this->dayIn($month, 20),
                'merchant' => 'Acme Corporation',
                'note' => '13th month pay',
            ]);
        }

        if ($month->isSameMonth(today()->copy()->subMonth())) {
            $this->write($user, [
                'account' => $accounts['E-Wallet'],
                'category' => $categories['Freelance'] ?? null,
                'type' => TransactionType::Income,
                'amount' => 850000,
                'date' => $this->dayIn($month, 22),
                'merchant' => 'Design Project',
                'note' => 'Logo and brand kit',
            ]);
        }
    }

    /**
     * @param  Collection<string, Account>  $accounts
     * @param  Collection<string, Category>  $categories
     */
    private function fixedCosts(User $user, Collection $accounts, Collection $categories, Carbon $month): void
    {
        /*
         * The demo is built around a ₱55,000 monthly take-home (two paydays),
         * so these add up to roughly a third of it and leave room for variable
         * spending and savings to still land on a positive month.
         */
        $fixed = [
            ['Rent', 1200000, 'Sunrise Apartments', 3, 'Savings Account'],
            ['Utilities', 287500, 'City Electric', 8, 'Savings Account'],
            ['Internet & Phone', 169900, 'Fiber Internet', 10, 'Savings Account'],
            ['Subscriptions', 54900, 'Netflix', 5, 'Credit Card'],
            ['Subscriptions', 14900, 'Spotify', 5, 'Credit Card'],
            ['Insurance', 125000, 'HealthFirst', 12, 'Savings Account'],
        ];

        foreach ($fixed as [$category, $amount, $merchant, $day, $account]) {
            $date = $this->dayIn($month, $day);

            if ($date->isAfter(today())) {
                continue;
            }

            $this->write($user, [
                'account' => $accounts[$account],
                'category' => $categories[$category] ?? null,
                'type' => TransactionType::Expense,
                'amount' => $amount,
                'date' => $date,
                'merchant' => $merchant,
            ]);
        }
    }

    /**
     * @param  Collection<string, Account>  $accounts
     * @param  Collection<string, Category>  $categories
     */
    private function groceries(User $user, Collection $accounts, Collection $categories, Carbon $month, bool $partial): void
    {
        $merchants = ['Metro Supermarket', 'Corner Grocery', 'Fresh Market', 'Wholesale Club'];

        for ($week = 0; $week < 4; $week++) {
            $date = $this->dayIn($month, 4 + ($week * 7));

            if ($date->isAfter(today())) {
                continue;
            }

            $this->write($user, [
                'account' => $accounts[$week % 2 === 0 ? 'Credit Card' : 'Cash Wallet'],
                'category' => $categories['Groceries'] ?? null,
                'type' => TransactionType::Expense,
                'amount' => random_int(120000, 240000),
                'date' => $date,
                'merchant' => $merchants[$week % count($merchants)],
            ]);
        }
    }

    /**
     * Small, frequent spending — the texture that makes reports interesting.
     *
     * @param  Collection<string, Account>  $accounts
     * @param  Collection<string, Category>  $categories
     */
    private function dailyLife(User $user, Collection $accounts, Collection $categories, Carbon $month, bool $partial): void
    {
        $patterns = [
            ['Coffee & Snacks', ['Daily Grind Coffee', 'Bean There Cafe'], 12000, 25000, 8, 'E-Wallet'],
            ['Restaurants', ['Noodle House', 'Grill Station', 'Pizza Corner', 'Sushi Bar'], 35000, 90000, 4, 'Credit Card'],
            ['Food Delivery', ['QuickEats', 'DoorDrop'], 38000, 70000, 2, 'E-Wallet'],
            ['Ride-hailing', ['RideShare'], 15000, 40000, 5, 'E-Wallet'],
            ['Fuel', ['Shell Station', 'Petrol Plus'], 150000, 220000, 1, 'Credit Card'],
            ['Public Transport', ['Metro Transit'], 5000, 12000, 8, 'Cash Wallet'],
            ['Pharmacy', ['Wellness Pharmacy'], 25000, 60000, 1, 'Cash Wallet'],
            ['Shopping', ['Online Marketplace', 'Department Store'], 55000, 200000, 1, 'Credit Card'],
            ['Entertainment', ['Cineplex', 'Arcade Zone'], 35000, 70000, 1, 'Credit Card'],
            ['Personal Care', ['Style Studio'], 35000, 60000, 1, 'Cash Wallet'],
            ['Household Supplies', ['Home Essentials'], 45000, 90000, 1, 'Cash Wallet'],
            ['Fitness', ['City Gym'], 150000, 150000, 1, 'Savings Account'],
        ];

        $lastDay = $partial ? min((int) today()->day, $month->daysInMonth) : $month->daysInMonth;

        foreach ($patterns as [$category, $merchants, $min, $max, $frequency, $account]) {
            $occurrences = $partial ? max(1, (int) round($frequency * $lastDay / $month->daysInMonth)) : $frequency;

            for ($i = 0; $i < $occurrences; $i++) {
                $date = $this->dayIn($month, random_int(1, $lastDay));

                if ($date->isAfter(today())) {
                    continue;
                }

                $this->write($user, [
                    'account' => $accounts[$account],
                    'category' => $categories[$category] ?? null,
                    'type' => TransactionType::Expense,
                    'amount' => random_int($min, $max),
                    'date' => $date,
                    'merchant' => $merchants[array_rand($merchants)],
                ]);
            }
        }
    }

    /**
     * @param  Collection<string, Account>  $accounts
     */
    private function transfers(User $user, Collection $accounts, Carbon $month): void
    {
        // Cash has no income of its own, so it is funded by a withdrawal the
        // way a real wallet is — without this the demo wallet runs negative.
        $this->transferPair(
            $user,
            $accounts['Savings Account'],
            $accounts['Cash Wallet'],
            600000,
            $this->dayIn($month, 2),
            'Cash withdrawal',
        );

        $this->transferPair(
            $user,
            $accounts['Savings Account'],
            $accounts['E-Wallet'],
            500000,
            $this->dayIn($month, 16),
            'Top up for daily spending',
        );

        $payment = $this->dayIn($month, 25);

        if ($payment->isAfter(today())) {
            return;
        }

        $this->transferPair(
            $user,
            $accounts['Savings Account'],
            $accounts['Credit Card'],
            1200000,
            $payment,
            'Credit card payment',
        );
    }

    /**
     * Write both legs of a transfer, skipping dates that have not arrived yet.
     */
    private function transferPair(User $user, Account $from, Account $to, int $amount, Carbon $date, string $note): void
    {
        if ($date->isAfter(today())) {
            return;
        }

        $group = (string) Str::uuid();

        $this->write($user, [
            'account' => $from,
            'category' => null,
            'type' => TransactionType::Transfer,
            'direction' => TransactionDirection::Outflow,
            'amount' => $amount,
            'date' => $date,
            'merchant' => $to->name,
            'note' => $note,
            'transfer_group' => $group,
            'transfer_account' => $to,
        ]);

        $this->write($user, [
            'account' => $to,
            'category' => null,
            'type' => TransactionType::Transfer,
            'direction' => TransactionDirection::Inflow,
            'amount' => $amount,
            'date' => $date,
            'merchant' => $from->name,
            'note' => $note,
            'transfer_group' => $group,
            'transfer_account' => $from,
        ]);
    }

    /**
     * @param  Collection<string, Account>  $accounts
     * @param  Collection<string, Category>  $categories
     */
    private function savings(User $user, Collection $accounts, Collection $categories): void
    {
        $goals = [
            ['Emergency Fund', 10000000, 6, [400000, 400000, 400000]],
            ['Japan Trip', 12000000, 10, [250000, 250000, 250000]],
            ['New Laptop', 6500000, 4, [200000, 250000, 150000]],
        ];

        foreach ($goals as $order => [$name, $target, $monthsAhead, $contributions]) {
            /** @var SavingsGoal $goal */
            $goal = $user->savingsGoals()->create([
                'account_id' => $accounts['Savings Account']->getKey(),
                'name' => $name,
                'currency_code' => $user->currency_code,
                'target_amount_minor' => $target,
                'target_date' => today()->copy()->addMonths($monthsAhead)->toDateString(),
                'color' => ['#2563eb', '#f97316', '#14b8a6'][$order] ?? null,
                'icon' => ['shield', 'plane', 'laptop'][$order] ?? null,
                'sort_order' => $order,
            ]);

            foreach ($contributions as $index => $amount) {
                // Early in the month so the current month's contribution is
                // always already in the past.
                $date = $this->dayIn(
                    today()->copy()->subMonths(count($contributions) - $index - 1)->startOfMonth(),
                    2,
                );

                if ($date->isAfter(today())) {
                    continue;
                }

                $goal->contributions()->create([
                    'amount_minor' => $amount,
                    'contributed_on' => $date->toDateString(),
                    'note' => 'Monthly set aside',
                ]);
            }

            $this->savingsGoals->recalculate($goal);
        }
    }

    /**
     * @param  Collection<string, Account>  $accounts
     * @param  Collection<string, Category>  $categories
     */
    private function bills(User $user, Collection $accounts, Collection $categories): void
    {
        $bills = [
            ['Electricity', 'City Electric', 287500, 'Utilities', 8, BillRecurrence::Monthly, 3],
            ['Internet', 'Fiber Internet', 169900, 'Internet & Phone', 10, BillRecurrence::Monthly, 2],
            ['Rent', 'Sunrise Apartments', 1500000, 'Rent', 3, BillRecurrence::Monthly, 5],
            ['Netflix', 'Netflix', 54900, 'Subscriptions', 5, BillRecurrence::Monthly, 1],
            ['Car Insurance', 'HealthFirst', 1850000, 'Insurance', 18, BillRecurrence::Yearly, 14],
        ];

        foreach ($bills as [$name, $payee, $amount, $category, $day, $recurrence, $remind]) {
            $due = $this->nextOccurrenceOfDay($day);

            $user->bills()->create([
                'account_id' => $accounts['Savings Account']->getKey(),
                'category_id' => $categories->get($category)?->getKey(),
                'name' => $name,
                'payee' => $payee,
                'amount_minor' => $amount,
                'currency_code' => $user->currency_code,
                'due_date' => $due->toDateString(),
                'recurrence' => $recurrence->value,
                'reminder_days_before' => $remind,
                'last_paid_on' => $due->copy()->subMonth()->toDateString(),
            ]);
        }
    }

    /**
     * @param  Collection<string, Account>  $accounts
     * @param  Collection<string, Category>  $categories
     */
    private function recurringRules(User $user, Collection $accounts, Collection $categories): void
    {
        $rules = [
            ['Payroll', TransactionType::Income, 'Salary', 2750000, RecurrenceFrequency::Monthly, 16, 'Savings Account', 'Acme Corporation'],
            ['Monthly rent', TransactionType::Expense, 'Rent', 1500000, RecurrenceFrequency::Monthly, 3, 'Savings Account', 'Sunrise Apartments'],
            ['Netflix', TransactionType::Expense, 'Subscriptions', 54900, RecurrenceFrequency::Monthly, 5, 'Credit Card', 'Netflix'],
        ];

        foreach ($rules as [$name, $type, $category, $amount, $frequency, $day, $account, $merchant]) {
            $user->recurringRules()->create([
                'account_id' => $accounts[$account]->getKey(),
                'category_id' => $categories->get($category)?->getKey(),
                'name' => $name,
                'type' => $type->value,
                'amount_minor' => $amount,
                'currency_code' => $user->currency_code,
                'merchant' => $merchant,
                'frequency' => $frequency->value,
                'interval' => 1,
                'by_month_day' => $day,
                'start_date' => today()->copy()->subMonths(3)->toDateString(),
                'next_run_on' => $this->nextOccurrenceOfDay($day)->toDateString(),
                'is_active' => true,
            ]);
        }
    }

    /**
     * @param  Collection<string, Category>  $categories
     */
    private function budget(User $user, Collection $categories): void
    {
        $budget = $this->budgets->ensureForMonth($user, today());

        $budget->forceFill(['overall_amount_minor' => 4000000])->save();

        /*
         * Limits sit close to what the sample spending actually comes to, so a
         * guest sees a mix of healthy categories and one or two that are
         * close to the line — which is what makes the alerts legible.
         */
        $limits = [
            'Groceries' => 800000,
            'Restaurants' => 300000,
            'Coffee & Snacks' => 180000,
            'Food Delivery' => 150000,
            'Ride-hailing' => 150000,
            'Fuel' => 250000,
            'Shopping' => 200000,
            'Entertainment' => 100000,
            'Utilities' => 350000,
            'Household Supplies' => 100000,
        ];

        $lines = [];

        foreach ($limits as $name => $amount) {
            if ($categories[$name] ?? null) {
                $lines[] = ['category_id' => $categories[$name]->getKey(), 'amount_minor' => $amount];
            }
        }

        $this->budgets->setCategoryLimits($budget, $lines);
    }

    /**
     * Two sample receipts: one already saved against a transaction, and one
     * waiting on the review screen with items spanning two categories, which
     * is what makes the split flow explorable without a camera.
     *
     * @param  Collection<string, Account>  $accounts
     * @param  Collection<string, Category>  $categories
     */
    private function receipts(User $user, Collection $accounts, Collection $categories): void
    {
        $pendingLines = [
            ['Whole Wheat Bread', 1, 8900],
            ['Fresh Milk 1L', 2, 11500],
            ['Free Range Eggs', 1, 18900],
            ['Bananas 1kg', 1, 7500],
            ['Chicken Breast 500g', 1, 24500],
            ['Dish Soap', 1, 13900],
            ['Laundry Detergent', 1, 28900],
            ['Paper Towels', 2, 9900],
        ];

        $subtotal = array_sum(array_map(fn (array $line): int => $line[1] * $line[2], $pendingLines));
        $tax = (int) round($subtotal * 0.12);

        $receipt = $this->writeReceipt($user, [
            'merchant' => 'Metro Supermarket',
            'date' => today()->copy()->subDays(1),
            'subtotal' => $subtotal,
            'tax' => $tax,
            'total' => $subtotal + $tax,
            'payment_method' => 'Cash',
            'status' => ReceiptStatus::Processed,
            'lines' => $pendingLines,
            'categories' => $categories,
        ]);

        $this->duplicates->apply($receipt);

        $coffeeLines = [['Iced Latte Grande', 1, 18500], ['Blueberry Muffin', 1, 9500]];
        $coffeeTotal = 28000;

        $confirmed = $this->writeReceipt($user, [
            'merchant' => 'Daily Grind Coffee',
            'date' => today()->copy()->subDays(3),
            'subtotal' => $coffeeTotal,
            'tax' => null,
            'total' => $coffeeTotal,
            'payment_method' => 'E-Wallet',
            'status' => ReceiptStatus::Confirmed,
            'lines' => $coffeeLines,
            'categories' => $categories,
        ]);

        $transaction = $this->write($user, [
            'account' => $accounts['E-Wallet'],
            'category' => $categories['Coffee & Snacks'] ?? null,
            'type' => TransactionType::Expense,
            'amount' => $coffeeTotal,
            'date' => today()->copy()->subDays(3),
            'merchant' => 'Daily Grind Coffee',
            'created_via' => CreatedVia::Receipt,
        ]);

        $transaction->forceFill(['receipt_id' => $confirmed->getKey()])->saveQuietly();
        $confirmed->forceFill(['confirmed_at' => now()])->saveQuietly();
    }

    /**
     * @param  array<string, mixed>  $data
     */
    private function writeReceipt(User $user, array $data): Receipt
    {
        $lines = $data['lines'];
        $rawText = $this->receiptText($data);
        $image = $this->receiptImage($data['merchant'], $rawText);

        $directory = "u{$user->getKey()}";
        $name = Str::uuid()->toString();
        $path = "{$directory}/{$name}.png";

        Storage::disk(config('budget.receipts.disk'))->put($path, $image);

        /** @var Receipt $receipt */
        $receipt = $user->receipts()->create([
            'image_path' => $path,
            'original_filename' => 'sample-receipt.png',
            'mime_type' => 'image/png',
            'size_bytes' => strlen($image),
            'status' => $data['status']->value,
            'merchant' => $data['merchant'],
            'receipt_date' => $data['date']->toDateString(),
            'currency_code' => $user->currency_code,
            'subtotal_minor' => $data['subtotal'],
            'tax_minor' => $data['tax'],
            'total_minor' => $data['total'],
            'payment_method' => $data['payment_method'],
            'raw_text' => $rawText,
            'is_demo' => true,
        ]);

        $receipt->forceFill([
            'content_hash' => hash('sha256', $image),
            'ocr_driver' => 'heuristic',
            'ocr_confidence' => 0.88,
            'field_confidence' => [
                'merchant' => 0.95,
                'receipt_date' => 0.9,
                'total_minor' => 0.95,
                'items' => 0.6,
            ],
            'processed_at' => now(),
        ])->saveQuietly();

        foreach ($lines as $index => [$description, $quantity, $unit]) {
            $suggested = $this->suggestDemoCategory($description, $data['categories']);

            $receipt->items()->create([
                'line_no' => $index,
                'description' => $description,
                'quantity' => $quantity,
                'unit_price_minor' => $unit,
                'total_minor' => $unit * $quantity,
                'suggested_category_id' => $suggested,
                'confidence' => $suggested === null ? 0.4 : 0.75,
            ]);
        }

        return $receipt->refresh()->load('items');
    }

    /**
     * @param  Collection<string, Category>  $categories
     */
    private function suggestDemoCategory(string $description, Collection $categories): ?int
    {
        $household = ['soap', 'detergent', 'towel', 'tissue', 'cleaner'];
        $coffee = ['latte', 'coffee', 'muffin', 'espresso', 'tea'];
        $lower = Str::lower($description);

        foreach ($household as $word) {
            if (str_contains($lower, $word)) {
                return $categories->get('Household Supplies')?->getKey();
            }
        }

        foreach ($coffee as $word) {
            if (str_contains($lower, $word)) {
                return $categories->get('Coffee & Snacks')?->getKey();
            }
        }

        return $categories->get('Groceries')?->getKey();
    }

    /**
     * @param  array<string, mixed>  $data
     */
    private function receiptText(array $data): string
    {
        $currency = (string) config('budget.demo.currency');
        $lines = [
            Str::upper($data['merchant']),
            '123 Market Street',
            'Tel: (02) 8123-4567',
            '',
            'Date: '.$data['date']->format('Y-m-d'),
            'Receipt #: '.random_int(100000, 999999),
            '',
        ];

        foreach ($data['lines'] as [$description, $quantity, $unit]) {
            $lines[] = sprintf(
                '%-28s %8s',
                $quantity > 1 ? "{$quantity} x {$description}" : $description,
                number_format(($unit * $quantity) / 100, 2),
            );
        }

        $lines[] = '';
        $lines[] = sprintf('%-28s %8s', 'SUBTOTAL', number_format($data['subtotal'] / 100, 2));

        if ($data['tax'] !== null) {
            $lines[] = sprintf('%-28s %8s', 'VAT 12%', number_format($data['tax'] / 100, 2));
        }

        $lines[] = sprintf('%-28s %8s', 'TOTAL', number_format($data['total'] / 100, 2));
        $lines[] = sprintf('%-28s %8s', Str::upper($data['payment_method']), number_format($data['total'] / 100, 2));
        $lines[] = '';
        $lines[] = "Thank you for shopping with us! ({$currency})";

        return implode("\n", $lines);
    }

    /**
     * Render the sample receipt text onto a small image so the demo has
     * something to show on the receipt screen.
     */
    private function receiptImage(string $merchant, string $text): string
    {
        $lines = explode("\n", $text);
        $width = 420;
        $lineHeight = 16;
        $height = ($lineHeight * count($lines)) + 48;

        $image = imagecreatetruecolor($width, $height);
        $white = imagecolorallocate($image, 252, 252, 250);
        $ink = imagecolorallocate($image, 38, 38, 38);
        $grey = imagecolorallocate($image, 150, 150, 150);

        imagefilledrectangle($image, 0, 0, $width, $height, $white);
        imagerectangle($image, 4, 4, $width - 5, $height - 5, $grey);

        foreach ($lines as $index => $line) {
            imagestring($image, 3, 18, 24 + ($index * $lineHeight), $line, $ink);
        }

        ob_start();
        imagepng($image);
        $contents = (string) ob_get_clean();
        imagedestroy($image);

        return $contents;
    }

    /**
     * @param  array<string, mixed>  $data
     */
    private function write(User $user, array $data): Transaction
    {
        $type = $data['type'];
        $account = $data['account'];

        $transaction = new Transaction;

        $transaction->fill([
            'account_id' => $account->getKey(),
            'transfer_account_id' => isset($data['transfer_account']) ? $data['transfer_account']->getKey() : null,
            'category_id' => $data['category']?->getKey(),
            'type' => $type->value,
            'direction' => ($data['direction'] ?? $type->defaultDirection())->value,
            'amount_minor' => $data['amount'],
            'currency_code' => $account->currency_code,
            'occurred_on' => $data['date']->toDateString(),
            'merchant' => $data['merchant'] ?? null,
            'note' => $data['note'] ?? null,
            'transfer_group' => $data['transfer_group'] ?? null,
            'created_via' => ($data['created_via'] ?? CreatedVia::Demo)->value,
            'is_demo' => true,
        ]);

        $transaction->user_id = $user->getKey();
        $transaction->save();

        return $transaction;
    }

    private function dayIn(Carbon $month, int $day): Carbon
    {
        return $month->copy()->day(min($day, $month->daysInMonth));
    }

    /**
     * The next time a given day-of-month falls due, this month or next.
     */
    private function nextOccurrenceOfDay(int $day): Carbon
    {
        $candidate = today()->copy()->day(min($day, today()->daysInMonth));

        return $candidate->isBefore(today())
            ? $this->dayIn(today()->copy()->addMonth()->startOfMonth(), $day)
            : $candidate;
    }
}
