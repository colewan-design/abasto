<?php

namespace App\Notifications;

use App\Models\Budget;
use App\Models\BudgetCategory;
use App\Support\Money;
use Illuminate\Bus\Queueable;
use Illuminate\Notifications\Messages\MailMessage;
use Illuminate\Notifications\Notification;

class BudgetLimitExceeded extends Notification
{
    use Queueable;

    public function __construct(
        private readonly Budget $budget,
        private readonly BudgetCategory $line,
        private readonly int $spentMinor,
    ) {}

    /**
     * @return array<int, string>
     */
    public function via(object $notifiable): array
    {
        return ['database'];
    }

    public function toMail(object $notifiable): MailMessage
    {
        return (new MailMessage)
            ->subject("You went over your {$this->categoryName()} budget")
            ->line(sprintf(
                'You have spent %s against a %s limit for %s.',
                $this->spent()->format(),
                $this->limit()->format(),
                $this->budget->period_start->format('F Y'),
            ))
            ->line(sprintf('That is %s over.', $this->over()->format()));
    }

    /**
     * @return array<string, mixed>
     */
    public function toArray(object $notifiable): array
    {
        return [
            'type' => 'budget.exceeded',
            'title' => "Over your {$this->categoryName()} budget",
            'body' => sprintf(
                '%s spent of %s — %s over.',
                $this->spent()->format(),
                $this->limit()->format(),
                $this->over()->format(),
            ),
            'budget_id' => $this->budget->getKey(),
            'category_id' => $this->line->category_id,
            'period_start' => $this->budget->period_start->toDateString(),
            'spent_minor' => $this->spentMinor,
            'limit_minor' => $this->line->limitMinor(),
        ];
    }

    private function categoryName(): string
    {
        return $this->line->category?->name ?? 'category';
    }

    private function spent(): Money
    {
        return Money::ofMinor($this->spentMinor, $this->budget->currency_code);
    }

    private function limit(): Money
    {
        return Money::ofMinor($this->line->limitMinor(), $this->budget->currency_code);
    }

    private function over(): Money
    {
        return Money::ofMinor($this->spentMinor - $this->line->limitMinor(), $this->budget->currency_code);
    }
}
