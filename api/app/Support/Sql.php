<?php

namespace App\Support;

use Illuminate\Support\Facades\DB;

/**
 * Small portability helpers for the handful of places where aggregation needs
 * vendor-specific SQL. The app runs on MySQL/MariaDB and the test suite on
 * SQLite, so grouping by month has to work on both.
 */
final class Sql
{
    /**
     * An expression yielding `YYYY-MM` for a date column.
     */
    public static function monthKey(string $column): string
    {
        return match (DB::connection()->getDriverName()) {
            'sqlite' => "strftime('%Y-%m', {$column})",
            'pgsql' => "to_char({$column}, 'YYYY-MM')",
            'sqlsrv' => "FORMAT({$column}, 'yyyy-MM')",
            default => "DATE_FORMAT({$column}, '%Y-%m')",
        };
    }
}
