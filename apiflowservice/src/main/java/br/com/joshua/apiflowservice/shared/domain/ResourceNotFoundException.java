package br.com.joshua.apiflowservice.shared.domain;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String code, String message) {
        super(code, message);
    }
}
