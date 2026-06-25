package br.com.gestaodireta.farm.dto;

public record FarmAccessPermissions(
        boolean canViewFarm,
        boolean canEditFarm,
        boolean canChangeFarmStatus,
        boolean canManageFarmUsers,
        boolean canViewFinancial,
        boolean canManageTransactions,
        boolean canManageCategories,
        boolean canManageGlobalCategories,
        boolean canCreateFarm) {}
