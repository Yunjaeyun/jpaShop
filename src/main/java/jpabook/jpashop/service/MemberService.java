package jpabook.jpashop.service;

import jpabook.jpashop.domain.Member;
import jpabook.jpashop.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    /**
     * 회원가입
     */

    @Transactional
    public Long join(Member member){
        validateDuplicateMemberJoin(member);// 중복 회원가입 검증
        memberRepository.save(member);
        return member.getId();
    }

    private void validateDuplicateMemberJoin(Member member){
        List<Member> findMembers = memberRepository.findByName(member.getName());
        if(!findMembers.isEmpty()){
            throw new IllegalStateException("이미 존재하는 회원입니다");
        }
    }

    /**
     * 회원조회
     */
    //전체
    public List<Member> findMembers(){
        return memberRepository.findAll();
    }

    //단일
    public Member findOne(Long memberId){
        return memberRepository.findOne(memberId);
    }

    /**
     * 회원수정
     * @param id
     * @param rename
     */
    @Transactional
    public void update(Long id, String rename) {
        Member member = memberRepository.findOne(id);
        member.setName(rename);
    }
}
