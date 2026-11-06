<?php

namespace App\Notifications;

use App\Models\Bill;
use Illuminate\Bus\Queueable;
use Illuminate\Notifications\Messages\MailMessage;
use Illuminate\Notifications\Notification;

class BillDueReminder extends Notification
{
    use Queueable;

    public function __construct(
        private readonly Bill $bill,
        private readonly int $daysUntilDue,
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
            ->subject("{$this->bill->name} is {$this->timing()}")
            ->line(sprintf(
                '%s of %s is %s (%s).',
                $this->bill->name,
                $this->bill->amount()->format(),
                $this->timing(),
                $this->bill->due_date->format('M j, Y'),
            ))
            ->line('Mark it paid in the app once you have settled it, and the payment will be recorded for you.');
    }

    /**
     * @return array<string, mixed>
     */
    public function toArray(object $notifiable): array
    {
        return [
            'type' => $this->daysUntilDue < 0 ? 'bill.overdue' : 'bill.due',
            'title' => "{$this->bill->name} is {$this->timing()}",
            'body' => sprintf('%s due %s.', $this->bill->amount()->format(), $this->bill->due_date->format('M j')),
            'bill_id' => $this->bill->getKey(),
            'due_date' => $this->bill->due_date->toDateString(),
            'amount_minor' => $this->bill->amount_minor,
            'days_until_due' => $this->daysUntilDue,
        ];
    }

    private function timing(): string
    {
        return match (true) {
            $this->daysUntilDue < 0 => abs($this->daysUntilDue).' '.str('day')->plural(abs($this->daysUntilDue)).' overdue',
            $this->daysUntilDue === 0 => 'due today',
            $this->daysUntilDue === 1 => 'due tomorrow',
            default => "due in {$this->daysUntilDue} days",
        };
    }
}
