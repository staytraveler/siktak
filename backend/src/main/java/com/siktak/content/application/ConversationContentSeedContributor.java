package com.siktak.content.application;

import org.springframework.stereotype.Component;

import com.siktak.common.seed.SeedContext;
import com.siktak.common.seed.SeedContributor;

import lombok.RequiredArgsConstructor;

/** demo-v1 시드에 대화 콘텐츠(seed/conversation-contents.json)를 붙인다 (docs/demo-scenario.md 7장). */
@Component
@RequiredArgsConstructor
public class ConversationContentSeedContributor implements SeedContributor {

    private final ConversationContentSeeder conversationContentSeeder;

    @Override
    public int order() {
        return 200;
    }

    @Override
    public void contribute(SeedContext context) {
        conversationContentSeeder.seedIfEmpty();
    }
}
