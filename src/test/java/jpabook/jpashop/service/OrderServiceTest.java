package jpabook.jpashop.service;

import jakarta.persistence.EntityManager;
import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Item.Book;
import jpabook.jpashop.domain.Member;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.exception.NotEnoughStockException;
import jpabook.jpashop.repository.OrderRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@RunWith(SpringRunner.class)
@Transactional
class OrderServiceTest {

    @Autowired
    EntityManager em;

    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;

    @Test
    void 상품_주문() throws Exception{
        Member member = createMember();

        Book book = createBook();

        int orderCount=2;

        //when
        Long orderId = orderService.Order(member.getId(), book.getId(), orderCount);

        //then
        Order getOrder = orderRepository.findOne(orderId);

        assertEquals( OrderStatus.ORDER,getOrder.getStatus(),"상품주문시 상태는 ORDER");
        assertEquals(getOrder.getOrderItems().size(),1,"주문한 상품 종류 수가 정확해야한다");
        assertEquals(getOrder.getTotalPrice(),20000,"주문가격은 가격 * 수량이다");
        assertEquals(book.getStockQuantity(),8,"주문 수량만큼 재고가 줄어야한다.");
    }



    @Test
    void 주문_취소() throws Exception{
        Member member = createMember();

        Book book = createBook();
        int orderCount=2;

        Long orderId = orderService.Order(member.getId(), book.getId(), orderCount);

        //when
        orderService.cancelOrder(orderId);

        //then
        Order getOrder = orderRepository.findOne(orderId);

        assertEquals(OrderStatus.CANCEL,getOrder.getStatus() ,"주문취소시 상태는 CANCEL이다");
        assertEquals(10,book.getStockQuantity(),"주문이 취소된 상품은 그만큼 재고가 증가해야한다.");
    }

    @Test
    void 재고_수량초과()throws Exception{

        Member member = createMember();

        Book book = createBook();

        int orderCount=11;

        Assertions.assertThrows(NotEnoughStockException.class, () -> {
            orderService.Order(member.getId(), book.getId(), orderCount);
        });

    }
    private Book createBook() {
        Book book=new Book();
        book.setName("영한 JPA1");
        book.setPrice(10000);
        book.setStockQuantity(10);
        em.persist(book);
        return book;
    }

    private Member createMember() {
        Member member=new Member();
        member.setName("가나다");
        member.setAddress(new Address("서울","대로","1302"));
        em.persist(member);
        return member;
    }

}