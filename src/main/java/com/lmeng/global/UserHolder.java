package com.lmeng.global;

import com.lmeng.dto.UserDTO;

public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }

//    private static final ThreadLocal<User> tl = new ThreadLocal<>();
//
//    public static void saveUser(User user){
//        tl.set(user);
//    }
//
//    public static User getUser(){
//        return tl.get();
//    }
//
//    public static void removeUser(){
//        tl.remove();
//    }
}
