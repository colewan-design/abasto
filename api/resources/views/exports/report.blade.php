@php
    use App\Support\Money;

    $money = fn (?int $minor) => Money::ofMinor((int) $minor, $user->currency_code)->format();
    $percent = fn (?float $value) => $value === null ? '—' : number_format($value * 100, 1).'%';
@endphp
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <title>{{ $from->format('F Y') }} report</title>
    <style>
        @page { margin: 28px 32px; }
        body { font-family: 'DejaVu Sans', sans-serif; font-size: 10.5px; color: #1f2937; }
        h1 { font-size: 20px; margin: 0 0 2px; }
        h2 { font-size: 12.5px; margin: 22px 0 8px; padding-bottom: 4px; border-bottom: 1px solid #e5e7eb; }
        .muted { color: #6b7280; font-size: 9.5px; }
        table { width: 100%; border-collapse: collapse; }
        th { text-align: left; font-size: 9px; text-transform: uppercase; letter-spacing: .04em; color: #6b7280; padding: 5px 6px; border-bottom: 1px solid #e5e7eb; }
        td { padding: 5px 6px; border-bottom: 1px solid #f3f4f6; }
        td.num, th.num { text-align: right; }
        .cards { width: 100%; margin-top: 12px; }
        .cards td { border: none; padding: 0 6px 0 0; width: 25%; }
        .card { border: 1px solid #e5e7eb; border-radius: 6px; padding: 9px 11px; }
        .card .label { font-size: 8.5px; text-transform: uppercase; letter-spacing: .05em; color: #6b7280; }
        .card .value { font-size: 14px; font-weight: bold; margin-top: 3px; }
        .bar { height: 5px; background: #f3f4f6; border-radius: 3px; overflow: hidden; }
        .bar span { display: block; height: 5px; background: #2563eb; }
        .bar.over span { background: #ef4444; }
        .bar.near span { background: #f59e0b; }
        .pos { color: #15803d; }
        .neg { color: #b91c1c; }
    </style>
</head>
<body>
    <h1>{{ $from->format('F Y') }}</h1>
    <div class="muted">
        {{ $user->name }} · {{ $from->format('M j') }}–{{ $to->format('M j, Y') }} ·
        generated {{ $generatedAt->format('M j, Y g:i A') }}
    </div>

    <table class="cards">
        <tr>
            <td><div class="card"><div class="label">Income</div><div class="value pos">{{ $money($cashFlow['income_minor']) }}</div></div></td>
            <td><div class="card"><div class="label">Expenses</div><div class="value neg">{{ $money($cashFlow['expense_minor']) }}</div></div></td>
            <td><div class="card"><div class="label">Saved</div><div class="value">{{ $money($cashFlow['saved_minor']) }}</div></div></td>
            <td><div class="card"><div class="label">Net</div>
                <div class="value {{ $cashFlow['net_minor'] >= 0 ? 'pos' : 'neg' }}">{{ $money($cashFlow['net_minor']) }}</div>
            </div></td>
        </tr>
    </table>

    <h2>Spending by category</h2>
    @if (count($categories) === 0)
        <p class="muted">No expenses recorded in this period.</p>
    @else
        <table>
            <thead>
                <tr>
                    <th>Category</th>
                    <th>Group</th>
                    <th class="num">Transactions</th>
                    <th class="num">Share</th>
                    <th class="num">Amount</th>
                </tr>
            </thead>
            <tbody>
                @foreach ($categories as $row)
                    <tr>
                        <td>{{ $row['name'] }}</td>
                        <td class="muted">{{ $row['group'] ?? '—' }}</td>
                        <td class="num">{{ $row['transactions'] }}</td>
                        <td class="num">{{ $percent($row['share']) }}</td>
                        <td class="num">{{ $row['formatted'] }}</td>
                    </tr>
                @endforeach
            </tbody>
        </table>
    @endif

    @if ($budget !== null)
        <h2>Budget progress</h2>
        <table>
            <thead>
                <tr>
                    <th>Category</th>
                    <th class="num">Limit</th>
                    <th class="num">Spent</th>
                    <th class="num">Remaining</th>
                    <th style="width: 90px;">Used</th>
                </tr>
            </thead>
            <tbody>
                @foreach ($budget['categories'] as $line)
                    @php $used = min(1, $line['percent_used'] ?? 0); @endphp
                    <tr>
                        <td>{{ $line['category']->name ?? 'Category' }}</td>
                        <td class="num">{{ $line['formatted']['limit'] }}</td>
                        <td class="num">{{ $line['formatted']['spent'] }}</td>
                        <td class="num {{ $line['remaining_minor'] < 0 ? 'neg' : '' }}">{{ $line['formatted']['remaining'] }}</td>
                        <td>
                            <div class="bar {{ $line['status'] === 'exceeded' ? 'over' : ($line['status'] === 'approaching' ? 'near' : '') }}">
                                <span style="width: {{ number_format($used * 100, 0) }}%"></span>
                            </div>
                        </td>
                    </tr>
                @endforeach
                @if ($budget['overall']['limit_minor'] !== null)
                    <tr>
                        <td><strong>Overall</strong></td>
                        <td class="num"><strong>{{ $money($budget['overall']['limit_minor']) }}</strong></td>
                        <td class="num"><strong>{{ $money($budget['overall']['spent_minor']) }}</strong></td>
                        <td class="num"><strong>{{ $money($budget['overall']['remaining_minor']) }}</strong></td>
                        <td></td>
                    </tr>
                @endif
            </tbody>
        </table>
    @endif

    <h2>Top merchants</h2>
    @if (count($merchants) === 0)
        <p class="muted">No merchant activity in this period.</p>
    @else
        <table>
            <thead>
                <tr>
                    <th>Merchant</th>
                    <th class="num">Visits</th>
                    <th class="num">Average</th>
                    <th class="num">Total</th>
                </tr>
            </thead>
            <tbody>
                @foreach ($merchants as $row)
                    <tr>
                        <td>{{ $row['merchant'] }}</td>
                        <td class="num">{{ $row['visits'] }}</td>
                        <td class="num">{{ $money($row['average_minor']) }}</td>
                        <td class="num">{{ $row['formatted'] }}</td>
                    </tr>
                @endforeach
            </tbody>
        </table>
    @endif

    <h2>Accounts</h2>
    <table>
        <thead>
            <tr>
                <th>Account</th>
                <th>Type</th>
                <th class="num">Balance</th>
            </tr>
        </thead>
        <tbody>
            @foreach ($balances['accounts'] as $account)
                <tr>
                    <td>{{ $account['name'] }}</td>
                    <td class="muted">{{ str($account['type'])->replace('_', ' ')->title() }}</td>
                    <td class="num {{ $account['balance_minor'] < 0 ? 'neg' : '' }}">{{ $account['formatted'] }}</td>
                </tr>
            @endforeach
            <tr>
                <td colspan="2"><strong>Available</strong></td>
                <td class="num"><strong>{{ $balances['formatted']['available'] }}</strong></td>
            </tr>
            @if ($balances['owed_minor'] > 0)
                <tr>
                    <td colspan="2">Owed on credit</td>
                    <td class="num neg">{{ $balances['formatted']['owed'] }}</td>
                </tr>
            @endif
        </tbody>
    </table>
</body>
</html>
