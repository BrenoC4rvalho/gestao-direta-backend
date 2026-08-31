package br.com.gestaodireta.financial.dto;

public record FinancialTransactionExportFile(String filename, String contentType, byte[] content) {}
