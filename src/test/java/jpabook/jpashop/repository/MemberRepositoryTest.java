package jpabook.jpashop.repository;

import jpabook.jpashop.domain.Member;
import jpabook.jpashop.service.MemberService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
class MemberRepositoryTest {

    @Autowired MemberRepository memberRepository;
    @Autowired MemberService memberService;

    @Test
    void 회원가입() throws Exception{
        Member member = new Member();
        member.setName("kim");

        Long saveId = memberService.join(member);
        Assertions.assertEquals(member, memberRepository.findOne(saveId));
    }

    @Test
    void 중복_회원_검증() throws Exception{

        Member member1 = new Member();
        member1.setName("kim1");

        Member member2 = new Member();
        member2.setName("kim1");

        memberService.join(member1);
//        memberService.join(member2); //예외가 발생해야 정상!

        Assertions.assertThrows(IllegalStateException.class, ()-> {
                    memberService.join(member2);
                });

    }


}